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

<%@ include file="/casemgmt/taglibs.jsp" %>

<%
    if (session.getValue("user") == null)
        response.sendRedirect(request.getContextPath() + "/logout.htm");
    String curUser_no;
    curUser_no = (String) session.getAttribute("user");

    boolean bFirstLoad = request.getAttribute("status") == null;

%>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
"http://www.w3.org/TR/html4/loose.dtd">
<c:set var="ctx" value="${pageContext.request.contextPath}"
       scope="request"/>
<html>
    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.setNoteStaleDate.title"/></title>

        <link rel="stylesheet" type="text/css"
              href="<%= request.getContextPath() %>/oscarEncounter/encounterStyles.css">
        <!-- calendar stylesheet -->
        <link rel="stylesheet" type="text/css" media="all"
              href="<c:out value="${ctx}"/>/share/calendar/calendar.css"
              title="win2k-cold-1">

        <script src="<c:out value="${ctx}"/>/share/javascript/prototype.js"
                type="text/javascript"></script>
        <script src="<c:out value="${ctx}"/>/share/javascript/scriptaculous.js"
                type="text/javascript"></script>

        <script type="text/javascript">

            function validate() {
                var date = document.getElementById("staleDate");
                if (date.value == "") {
                    alert("Please select a date before saving");
                    return false;
                }

                return true;
            }
        </script>

    </head>

    <body class="BodyStyle" vlink="#0000FF">

    <table class="MainTable" id="scrollNumber1" name="encounterTable">
        <tr class="MainTableTopRow">
            <td class="MainTableTopRowLeftColumn"><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.setNoteStaleDate.msgPrefs"/></td>
            <td style="color: white" class="MainTableTopRowRightColumn"><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.setNoteStaleDate.msgProviderStaleDate"/></td>
        </tr>
        <tr>
            <td class="MainTableLeftColumn">&nbsp;</td>
            <td class="MainTableRightColumn">
                <%
                    if (request.getAttribute("status") == null) {

                %> 
                <form style="frmProperty" action="${pageContext.request.contextPath}/setProviderStaleDate.do" method="post">
                    <input type="hidden" id="method" name="method" value="save">
                    <input type="hidden" name="dateProperty.name" value="staleNoteDate"/>
                    <input type="hidden" name="dateProperty.providerNo" value="${providerNo}"/>

                    <fmt:setBundle basename="oscarResources"/><fmt:message key="provider.setNoteStaleDate.msgEdit"/>
                    <select name="dateProperty.value" id="dateProperty.value">
                        <c:forEach var="opt" items="${staleDateOptions}">
                            <option value="${opt.value}" <c:if test="${opt.value == dateProperty.value}">selected</c:if>>${opt.label}</option>
                        </c:forEach>
                    </select>
                    <br/>

                    <input type="hidden" name="singleViewProperty.name" value="staleFormat"/>
                    <input type="hidden" name="singleViewProperty.providerNo" value="${providerNo}"/>

                    Use Single Line View:
                    <select name="singleViewProperty.value" id="singleViewProperty.value">
                        <c:forEach var="opt" items="${viewOptions}">
                            <option value="${opt.value}" <c:if test="${opt.value == singleViewProperty.value}">selected</c:if>>${opt.label}</option>
                        </c:forEach>
                    </select>
                    <br/>

                    <input type="submit"
                        value="<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.setNoteStaleDate.btnSubmit"/>"/>
                    <input type="submit" onclick="$('method').value='remove';"
                        value="<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.setNoteStaleDate.btnReset"/>"/>
                </form> <%
            } else {
            %>  <fmt:setBundle basename="oscarResources"/><fmt:message key="provider.setNoteStaleDate.msgSuccess"/>
                <br>

                <%
                    }
                %>
            </td>
        </tr>
        <tr>
            <td class="MainTableBottomRowLeftColumn"></td>
            <td class="MainTableBottomRowRightColumn"></td>
        </tr>
    </table>
    </body>
</html>
