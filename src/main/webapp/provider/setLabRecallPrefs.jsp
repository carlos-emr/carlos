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
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/casemgmt/taglibs.jsp" %>
<%@ page import="java.util.*" %>
<%@ page import="java.util.ResourceBundle"%>
<%
    if (session.getValue("user") == null)
        response.sendRedirect(request.getContextPath() + "/logout.htm");

    ResourceBundle bundle = ResourceBundle.getBundle("oscarResources", request.getLocale());

    String providertitle = (String) request.getAttribute("providertitle");
    String providermsgPrefs = (String) request.getAttribute("providermsgPrefs");
    String providermsgProvider = (String) request.getAttribute("providermsgProvider");
    String providermsgEdit = (String) request.getAttribute("providermsgEdit");
    String providermsgSuccess = (String) request.getAttribute("providermsgSuccess");
%>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
    <head>
        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
        <title><%=bundle.getString(providertitle)%></title>
        <link rel="stylesheet" type="text/css" href="<%= request.getContextPath() %>/oscarEncounter/encounterStyles.css">
    </head>

    <body class="BodyStyle" vlink="#0000FF">

    <table class="MainTable" id="scrollNumber1" name="encounterTable">
        <tr class="MainTableTopRow">
            <td class="MainTableTopRowLeftColumn"><%=bundle.getString(providermsgPrefs)%></td>
            <td style="color: white" class="MainTableTopRowRightColumn"><%=bundle.getString(providermsgProvider)%></td>
        </tr>
        <tr>
            <td class="MainTableLeftColumn">&nbsp;</td>
            <td class="MainTableRightColumn">
                <%if (request.getAttribute("status") == null) {%>
                <%=bundle.getString(providermsgEdit)%>
                <form action="${pageContext.request.contextPath}/setProviderStaleDate.do" method="post">
                    <input type="hidden" name="method" value="<c:out value="${method}"/>">
                    <table>
                        <tr>
                            <td>Delegate: <font color="red">*required</font></td>
                            <td>
                                <select name="labRecallDelegate.value" id="labRecallDelegate.value" onchange="delegateCheck();">
                                    <c:forEach var="provider" items="${providerSelect}">
                                        <option value="${provider.value}" <c:if test="${provider.value == labRecallDelegate.value}">selected</c:if> >
                                                ${provider.label}
                                        </option>
                                    </c:forEach>
                                </select>
                            </td>
                        </tr>

                        <tr>
                            <td>Default Message Subject:</td>
                            <td><input type="text" name="labRecallMsgSubject.value" value="<c:out value='${subject.value}'/>" size="50" /></td>
                        </tr>

                        <tr>
                            <td>Tickler Assignee:</td>
                            <td><input type="checkbox" name="labRecallTicklerAssignee.checked" <c:if test="${labRecallTicklerAssignee.checked}">checked</c:if> />default to delegate</td>
                        </tr>

                        <tr>
                            <td>Tickler Priority:</td>
                            <td><select name="labRecallTicklerPriority.value" id="labRecallTicklerPriority.value">
                                <c:forEach var="priority" items="${prioritySelect}">
                                    <option value="${priority.value}" <c:if test="${priority.value == labRecallTicklerPriority.value}">selected</c:if> >
                                            ${priority.label}
                                    </option>
                                </c:forEach>
                            </select></td>
                        </tr>

                    </table>
                    <input type="submit" name="btnApply" value="Submit" />
                    <input type="button" name="delete" value="Delete" onclick="deleteProp();" style="display:none;">
                </form> <%} else {%> <%=bundle.getString(providermsgSuccess)%> <br>
                <%}%>
            </td>
        </tr>
        <tr>
            <td class="MainTableBottomRowLeftColumn"></td>
            <td class="MainTableBottomRowRightColumn"></td>
        </tr>
    </table>

    <script>
        function deleteProp() {
            var r = confirm("Are you sure you would like to delete the lab recall settings?");
            if (r == true) {
                document.forms[0].reset();
                document.forms[0]['labRecallDelegate.value'].value = "";
                document.forms[0].submit();
            }
        }

        function delegateCheck() {
            var delegate = document.forms[0]['labRecallDelegate.value'].value;
            if (delegate != "") {
                document.forms[0]['btnApply'].disabled = false;
            } else {
                document.forms[0]['btnApply'].disabled = true;
            }
        }

        delegateCheck();

        if (document.forms[0]['labRecallDelegate.value'].value != "") {
            document.forms[0]['delete'].style.display = "inline";
        }

    </script>
    </body>
</html>
