<!DOCTYPE html>
<%--

    Copyright (c) 2006-. OSCARservice, OpenSoft System. All Rights Reserved.
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

--%>
<%@ page language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri='http://java.sun.com/jsp/jstl/core' prefix="c" %>
<%@ page import="java.util.List" %>

<html>
<head>
    <title><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.endYearStatement"/></title>

    <script type="text/javascript" src="<%=request.getContextPath() %>/js/jquery-1.9.1.min.js"></script>
    <script src="<%=request.getContextPath() %>/js/bootstrap.min.js"></script>
    <script type="text/javascript" src="<%=request.getContextPath() %>/js/bootstrap-datepicker.js"></script>

    <link href="<%=request.getContextPath() %>/css/bootstrap.min.css" rel="stylesheet">
    <link href="<%=request.getContextPath() %>/css/datepicker.css" rel="stylesheet" type="text/css">
    <link rel="stylesheet" href="<%=request.getContextPath() %>/css/font-awesome.min.css">

    <script type="text/javascript">
        function popupPage(vheight, vwidth, varpage) { //open a new popup window
            var page = "" + varpage;
            windowprops = "height=" + vheight + ",width=" + vwidth + ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes,screenX=0,screenY=0,top=0,left=0";//360,680
            window.location = page;
        }

        function demographicSearch() {
            var search_param = $('#nameForlooksOnly').val();
            var url = '<%= request.getContextPath() %>/demographic/demographicsearch2reportresults.jsp';
            url += '?originalpage=' + escape('<%=request.getContextPath()%>/billing/CA/ON/endYearStatement.do?demosearch=true');
            url += '&search_mode=search_name';
            url += '&orderby=last_name, first_name';
            url += '&limit1=0&limit2=5';
            url += '&keyword=' + search_param;
            popupPage(700, 1000, url, 'master');
            return false;
        }

        function refresh() {
            var u = self.location.href;
            if (u.lastIndexOf("view=1") > 0) {
                self.location.href = u.substring(0, u.lastIndexOf("view=1")) + "view=0" + u.substring(eval(u.lastIndexOf("view=1") + 6));
            } else {
                history.go(0);
            }
        }

        function calToday(field) {
            var calDate = new Date();
            varMonth = calDate.getMonth() + 1;
            varMonth = varMonth > 9 ? varMonth : ("0" + varMonth);
            varDate = calDate.getDate() > 9 ? calDate.getDate() : ("0" + calDate.getDate());
            field.value = calDate.getFullYear() + '-' + (varMonth) + '-' + varDate;
        }

        function validateFields() {
            if ($('#nameForlooksOnly').val() == '') {
                alert('Please select a valid patient for this report.');
                return false;
            }
            return true;

        }

        //-->


    </script>

    <style>
        .well {
            padding-left: 8px;
            padding-right: 8px;
        }
    </style>
</head>

<%
    String name = "";
    if (request.getParameter("firstNameParam") != null && request.getParameter("lastNameParam") != null) {
        name = request.getParameter("firstNameParam") + " " + request.getParameter("lastNameParam");
    }
%>
<body>
<h3><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.endYearStatement"/></h3>

<div class="container-fluid">

    <div class="row well">
        <form action="${pageContext.request.contextPath}/billing/CA/ON/endYearStatement.do" method="post">
            <input type="hidden" name="demographicNoParam" id="demographicNoParam"/>

            <div class="span5">
                Patient Name: <br>
                <div class="input-append">
                    <input class="span4" id="nameForlooksOnly" type="text" value="<%=name%>">
                    <button class="btn btn-primary" type="button" value="Search" onclick="demographicSearch()"><i
                            class="icon icon-search"></i></button>
                </div>
            </div>

            <input type="hidden" name="firstNameParam" id="fname" value="<%=request.getParameter("firstNameParam")%>"/>
            <input type="hidden" name="lastNameParam" id="lname" value="<%=request.getParameter("lastNameParam")%>"/>


            <div class="span2">
                <label>Start Date:</label>
                <div class="input-append">
                    <input type="text" style="width:90px" name="fromDateParam" id="fromDateParam"
                           value="<%= request.getAttribute("fromDateParam") != null ? request.getAttribute("fromDateParam") : "" %>"
                           pattern="^\d{4}-((0\d)|(1[012]))-(([012]\d)|3[01])$" autocomplete="off"/>
                    <span class="add-on"><i class="icon-calendar"></i></span>
                </div>
            </div>


            <div class="span2">
                <label>End Date:</label>
                <div class="input-append">
                    <input type="text" style="width:90px" name="toDateParam" id="toDateParam"
                           value="<%= request.getAttribute("toDateParam") != null ? request.getAttribute("toDateParam") : "" %>"
                           pattern="^\d{4}-((0\d)|(1[012]))-(([012]\d)|3[01])$" autocomplete="off"/>
                    <span class="add-on"><i class="icon-calendar"></i></span>
                </div>
            </div>

            <div class="span10">
                <input class="btn" type="submit" name="search" value="Create Statement"
                       onclick="return validateFields();">

                <input class="btn" type="submit" name="pdf" value="Print PDF"
                       <c:if test="${empty result}">disabled="disabled"</c:if> >
            </div>
        </form>
    </div>

    <div class="row">

        <div style="color" red
        "><% 
    java.util.List<String> actionErrors = (java.util.List<String>) request.getAttribute("actionErrors");
    if (actionErrors != null && !actionErrors.isEmpty()) {
%>
    <div class="action-errors">
        <ul>
            <% for (String error : actionErrors) { %>
                <li><%= error %></li>
            <% } %>
        </ul>
    </div>
<% } %></div>

    <c:if test="${not empty summary}">
        <table class="table table-striped table-condensed">
            <tr>
                <td width="50px">&nbsp;</td>
                <td align="left" colspan="2">Patient Information</td>
            </tr>
            <tr>
                <td width="50px">&nbsp;</td>
                <td width="100px">Patient:</td>
                <td>
                    <c:out value="${summary.patientNo}"/>&nbsp;&nbsp;
                    <c:out value="${summary.patientName}"/>&nbsp;&nbsp;
                    <c:out value="${summary.hin}"/>&nbsp;&nbsp;
                </td>
            </tr>
            <tr>
                <td width="50px">&nbsp;</td>
                <td width="100px">Address :</td>
                <td>
                    <c:out value="${summary.address}"/>
                </td>
            </tr>
            <tr>
                <td width="50px">&nbsp;</td>
                <td width="100px">Phone :</td>
                <td>
                    <c:out value="${summary.phone}"/>
                </td>
            </tr>
        </table>
    </c:if>

    <br/>

    <c:if test="${not empty result}">
        <table class="table table-striped table-condensed">
            <tr bgcolor="#ccffcc">
                <th>INVOICE NUMBER</th>
                <th>INVOICE DATE</th>
                <th>SERVICE CODE</th>
                <th>INVOICED</th>
                <th>PAID</th>
            </tr>
            <c:forEach var="row" items="${result}" varStatus="counter">
                <tr bgcolor="#CEF6CE">
                    <td><c:out value="${row.invoiceNo}"/></td>
                    <td><c:out value="${row.invoiceDate}"/></td>
                    <td>&nbsp;</td>
                    <td><c:out value="${row.invoiced}"/></td>
                    <td><c:out value="${row.paid}"/></td>
                </tr>
                <c:forEach var="service" items="${row.services}" varStatus="counterService">
                    <tr bgcolor="${counterService.index % 2 == 0 ? 'ivory' : '#EEEEFF'}">
                        <td>&nbsp;</td>
                        <td>&nbsp;</td>
                        <td><c:out value="${service.code}"/></td>
                        <td><c:out value="${service.fee}"/></td>
                        <td>&nbsp;</td>
                    </tr>
                </c:forEach>
            </c:forEach>
            <tr height="10">
                <td colspan="5" style="border:collapse">&nbsp;</td>
            </tr>
            <tr bgcolor="#99FF66">
                <td>Count: &nbsp;&nbsp;&nbsp;
                    <c:if test="${not empty summary}"><c:out value="${summary.count}"/></c:if>
                </td>
                <td>&nbsp;</td>
                <td align="center">Total:</td>
                <td><c:if test="${not empty summary}"><c:out value="${summary.invoiced}"/></c:if></td>
                <td><c:if test="${not empty summary}"><c:out value="${summary.paid}"/></c:if></td>
            </tr>
        </table>
    </c:if>

</div><!--row-->


</div><!--container-->
</body>
<script type="text/javascript">
    var startDate = $("#fromDateParam").datepicker({format: "yyyy-mm-dd"});
    var endDate = $("#toDateParam").datepicker({format: "yyyy-mm-dd"});

</script>
</html>
