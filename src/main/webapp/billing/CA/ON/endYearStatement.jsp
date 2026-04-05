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


    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

--%>
<%@ page language="java" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri='jakarta.tags.core' prefix="c" %>
<%@ page import="java.util.List" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ page import="io.github.carlos_emr.carlos.util.StringUtils" %>

<html>
<head>
    <title><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.endYearStatement"/></title>

    <script src="<%=request.getContextPath() %>/library/bootstrap/5.3.3/js/bootstrap.bundle.min.js"></script>
    <script type="text/javascript" src="<%=request.getContextPath() %>/library/flatpickr/flatpickr.min.js"></script>

    <link href="<%=request.getContextPath() %>/library/bootstrap/5.3.3/css/bootstrap.min.css" rel="stylesheet">
    <link href="<%=request.getContextPath() %>/library/flatpickr/flatpickr.min.css" rel="stylesheet" type="text/css">
    <link rel="stylesheet" href="<%=request.getContextPath() %>/css/fontawesome-all.min.css">

    <script type="text/javascript">
        function popupPage(vheight, vwidth, varpage) { //open a new popup window
            var page = String(varpage).trim();
            // Security: block dangerous URI schemes to prevent XSS via window.location assignment
            if (/^(?:javascript|data|vbscript)\s*:/i.test(page)) { console.error('popupPage: blocked dangerous protocol'); return; }
            windowprops = "height=" + vheight + ",width=" + vwidth + ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes,screenX=0,screenY=0,top=0,left=0";//360,680
            window.location = page;
        }

        function demographicSearch() {
            var search_param = document.getElementById('nameForlooksOnly').value;
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
            if (document.getElementById('nameForlooksOnly').value == '') {
                alert('Please select a valid patient for this report.');
                return false;
            }
            return true;

        }

        //-->


    </script>

    <style>
        .card-body {
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

    <div class="row card card-body bg-body-tertiary">
        <form action="${pageContext.request.contextPath}/billing/CA/ON/endYearStatement.do" method="post">
            <input type="hidden" name="demographicNoParam" id="demographicNoParam"/>

            <div class="col-md-5">
                Patient Name: <br>
                <div class="input-group">
                    <input class="form-control" id="nameForlooksOnly" type="text" value="<%=Encode.forHtmlAttribute(name)%>">
                    <button class="btn btn-primary" type="button" value="Search" onclick="demographicSearch()"><i
                            class="fa-solid fa-magnifying-glass"></i></button>
                </div>
            </div>

            <input type="hidden" name="firstNameParam" id="fname" value="<%= Encode.forHtmlAttribute(StringUtils.noNull(request.getParameter("firstNameParam"))) %>"/>
            <input type="hidden" name="lastNameParam" id="lname" value="<%= Encode.forHtmlAttribute(StringUtils.noNull(request.getParameter("lastNameParam"))) %>"/>


            <div class="col-md-2">
                <label>Start Date:</label>
                <div class="input-group">
                    <input type="text" class="form-control" style="width:90px" name="fromDateParam" id="fromDateParam"
                           value="<%= Encode.forHtmlAttribute(request.getAttribute("fromDateParam") != null ? (String)request.getAttribute("fromDateParam") : "") %>"
                           pattern="^\d{4}-((0\d)|(1[012]))-(([012]\d)|3[01])$" autocomplete="off"/>
                    <span class="input-group-text"><i class="fa-solid fa-calendar"></i></span>
                </div>
            </div>


            <div class="col-md-2">
                <label>End Date:</label>
                <div class="input-group">
                    <input type="text" class="form-control" style="width:90px" name="toDateParam" id="toDateParam"
                           value="<%= Encode.forHtmlAttribute(request.getAttribute("toDateParam") != null ? (String)request.getAttribute("toDateParam") : "") %>"
                           pattern="^\d{4}-((0\d)|(1[012]))-(([012]\d)|3[01])$" autocomplete="off"/>
                    <span class="input-group-text"><i class="fa-solid fa-calendar"></i></span>
                </div>
            </div>

            <div class="col-md-10">
                <input class="btn btn-secondary" type="submit" name="search" value="Create Statement"
                       onclick="return validateFields();">

                <input class="btn btn-secondary" type="submit" name="pdf" value="Print PDF"
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
                <li><%= Encode.forHtml(error) %></li>
            <% } %>
        </ul>
    </div>
<% } %></div>

    <c:if test="${not empty summary}">
        <table class="table table-striped table-sm">
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
        <table class="table table-striped table-sm">
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
    flatpickr("#fromDateParam", {dateFormat: "Y-m-d", allowInput: true});
    flatpickr("#toDateParam", {dateFormat: "Y-m-d", allowInput: true});

</script>
</html>
