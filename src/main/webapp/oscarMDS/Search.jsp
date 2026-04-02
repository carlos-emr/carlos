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
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ page import="org.owasp.encoder.Encode" %>


<!DOCTYPE html>
<html>
<head>
    <title><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.search.title"/></title>
    <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
    <script type="text/javascript" src="<%= request.getContextPath() %>/js/checkDate.js"></script>

    <script type="text/javascript" src="<%= request.getContextPath() %>/library/jquery/jquery-3.7.1.min.js"></script>
    <script src="<%= request.getContextPath() %>/library/jquery/jquery-compat.js"></script>
    <script type="text/javascript"
            src="<%= request.getContextPath() %>/library/jquery/jquery-ui-1.14.2.min.js"></script>
    <link rel="stylesheet" href="<%= request.getContextPath() %>/library/jquery/jquery-ui-1.14.2.min.css"/>

    <script type="text/javascript">
        var readOnly = false;

        function onSubmitCheck() {
            if (!check_date('startDate')) {
                return false;
            }
            if (!check_date('endDate')) {
                return false;
            }

            var url = "<%=request.getContextPath()%>/documentManager/inboxManage.do?method=prepareForIndexPage&providerNo=<%= Encode.forUriComponent(request.getParameter("providerNo")) %>";
            if ($("#provfind").val().trim() != "") {
                url += "&searchProviderNo=" + $("#provfind").val().trim();
            } else {
                url += "&searchProviderNo=-1";
            }
            if ($("#lname").val().trim() != "") {
                url += "&lname=" + $("#lname").val().trim();
            }
            if ($("#fname").val().trim() != "") {
                url += "&fname=" + $("#fname").val().trim();
            }
            if ($("#hnum").val().trim() != "") {
                url += "&hnum=" + $("#hnum").val().trim();
            }
            if ($("#startDate").val().trim() != "") {
                url += "&startDate=" + $("#startDate").val().trim();
            }
            if ($("#endDate").val().trim() != "") {
                url += "&endDate=" + $("#endDate").val().trim();
            }
            if ($("input[name='searchProviderAll']").is(':checked')) {
                url += "&searchProviderAll=" + $("input[name='searchProviderAll']:checked").val();
            }
            if ($("input[name='status']").is(':checked')) {
                url += "&status=" + $("input[name='status']:checked").val();
            }

            $("#searchFrm").attr("action", url);

        }

        $(function () {

            $("#autocompleteprov").autocomplete({
                source: "<%= request.getContextPath() %>/provider/SearchProvider.do?method=labSearch",
                minLength: 2,
                focus: function (event, ui) {
                    $("#autocompleteprov").val(ui.item.label);
                    return false;
                },
                select: function (event, ui) {
                    $("#autocompleteprov").val(ui.item.label);
                    $("#provfind").val(ui.item.value);
                    return false;
                }
            })
        });

    </script>

    <style>

        body *:not(h2) {
            font-family: "Helvetica Neue", Helvetica, Arial, sans-serif;
            font-size: 12px;
        }

        #content-wrapper {
            margin: auto 15px;
        }

    </style>


</head>

<body>
<div id="content-wrapper">
    <form id="searchFrm" method="POST" action="" onSubmit="return onSubmitCheck();">
        <input type="hidden" name="method" value="prepareForIndexPage"/>
        <table style="width: 100%;height: 100vh;">
            <tr class="MainTableTopRow">
                <td class="MainTableTopRow">
                    <table style="width:100%;">
                        <tr>
                            <td>
                                <h2>Search All Inboxes</h2>
                            </td>
                            <td style="text-align: right;"><input type="button"
                                                                  value=" <fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnClose"/> "
                                                                  onClick="window.close()"></td>

                            <td>
                        </tr>
                    </table>
                </td>
            </tr>
            <tr>
                <td style="text-align: center; text-align: -moz-center;vertical-align: middle;height: 100%;">
                    <table style="width:400px; text-align: left;">
                        <tr>
                            <td><label for="lname"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.search.formPatientLastName"/>:</label>
                            </td>
                            <td><input type="text" id="lname" name="lname"></td>
                        </tr>
                        <tr>
                            <td><label for="fname"></label><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.search.formPatientFirstName"/>:</label>
                            </td>
                            <td><input type="text" id="fname" name="fname"></td>
                        </tr>
                        <tr>
                            <td><label for="hnum"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.search.formPatientHealthNumber"/>:</label>
                            </td>
                            <td><input type="text" id="hnum" name="hnum"></td>
                        </tr>

                        <tr>
                            <td><label for="startDate">Start Date:</label>
                            </td>
                            <td><input type="date" id="startDate" name="startDate"></td>
                        </tr>
                        <tr>
                            <td><label for="endDate">End Date:</label>
                            </td>
                            <td><input type="date" id="endDate" name="endDate"></td>
                        </tr>


                        <tr>
                            <td><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.search.formPhysician"/>:
                            </td>
                            <td><input type="radio" name="searchProviderAll" id="searchProviderAll-physician" value="-1"
                                       ondblclick="this.checked = false;">
                                <label for="searchProviderAll-physician"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.search.formPhysicianAll"/></label>
                                <input type="radio" name="searchProviderAll" id="searchProviderAll-unclaimed" value="0"
                                       ondblclick="this.checked = false;">
                                <label for="searchProviderAll-unclaimed"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.search.formPhysicianUnclaimed"/></label>
                                <input type="hidden" name="providerNo"
                                       value="<%= Encode.forHtmlAttribute(request.getParameter("providerNo")) %>">
                            </td>
                        </tr>
                        <tr>
                            <td>&nbsp;</td>
                            <td>
                                <input type="hidden" name="searchProviderNo" id="provfind"/>
                                <input type="text" id="autocompleteprov" name="demographicKeyword"/>
                            </td>
                        </tr>
                        <tr>
                            <td colspan="2">
                                <label><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.search.formReportStatus"/>: </label>
                                <input type="radio"
                                       name="status" id="status-all" value="">
                                <label for="status-all"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.search.formReportStatusAll"/></label>
                                <input type="radio"
                                       name="status" id="status-new" value="N" checked>
                                <label for="status-new"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.search.formReportStatusNew"/></label>
                                <input type="radio"
                                       name="status" id="status-ack" value="A">
                                <label for="status-ack"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.search.formReportStatusAcknowledged"/></label>
                                <input
                                        type="radio" name="status" id="status-filed" value="F">
                                <label for="status-filed">Filed</label>
                            </td>
                        </tr>
                        <tr>
                            <td colspan="2" style="text-align: right;">
                                <input type="submit"
                                       value=" <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.search.btnSearch"/> ">

                            </td>
                        </tr>
                    </table>
                </td>
            </tr>
        </table>
    </form>
</div>
</body>
</html>
