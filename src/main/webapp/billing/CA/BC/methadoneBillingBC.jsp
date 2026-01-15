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
<% if (session.getAttribute("user") == null) {
    response.sendRedirect(request.getContextPath() + "/logout.jsp");
} %>


<%--
	Author: OSCARprn by Treatment - support@oscarprn.com
	Support: Treatment
	Date: Dec 2016
 --%>

<%@page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@page import="java.util.*,
                ca.openosp.openo.util.*,
                org.springframework.web.context.support.WebApplicationContextUtils,
                org.springframework.web.context.WebApplicationContext" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<html>
<head>
    <title>Oscar Methadone Billing</title>

    <link rel="stylesheet" href="<c:out value="${ oscar_context_path }/css/bcbilling.css" />" type="text/css"
          media="screen"/>
    <link rel="stylesheet" href="<c:out value="${ oscar_context_path }/css/methadoneBillingBC.css" />" type="text/css"
          media="screen"/>
    <link rel="stylesheet" href="<c:out value="${ oscar_context_path }/css/cupertino/jquery-ui-1.8.18.custom.css" />"
          type="text/css"/>
    <link rel="stylesheet" href="<c:out value="${ oscar_context_path }/css/jquery.ui.autocomplete.css" />"
          type="text/css"/>
    <style type="text/css">.ui-autocomplete-loading {
        background: white url('images/ui-anim_basic_16x16.gif') right center no-repeat;
    }
    </style>
    <script type="text/javascript" src="<c:out value="${ oscar_context_path }/js/jquery-1.7.1.min.js" />"></script>
    <script type="text/javascript" src="<c:out value="${ oscar_context_path }/js/jquery.ui.position.js" />"></script>
    <script type="text/javascript" src="<c:out value="${ oscar_context_path }/js/jquery.ui.widget.js" />"></script>
    <script type="text/javascript"
            src="<c:out value="${ oscar_context_path }/js/jquery-ui-1.8.18.custom.min.js" />"></script>
    <script type="text/javascript">

        $(document).ready(function () {


            if (<c:out value="${not empty requestScope.saved}" />) {
                $("#saved").slideDown("fast");
            }

            // date picker function. Don't want it to run
            // until other elements are chosen.
            $('#serviceDate').datepicker({
                dateFormat: "yy-mm-dd",
                buttonImage: "images/datepicker.gif",
                beforeShow: function (input, inst) {
                    // Note: Unlike quickBillingBC.jsp, this form does not have a visitLocation
                    // dropdown, so we only validate the billing provider selection here.
                    if ($("#billingProviderNo :selected").val() == "empty") {
                        alert("Select Billing Physician");
                        $("#billingProviderNo").focus();
                    }
                },
                onClose: function (input, inst) {

                    if ($("#saved").is(":visible")) {

                        $("#saved").slideUp("fast");
                    }

                    $("#ptName").focus();

                }
            });

            // serialize for insertion into JSON
            $.fn.serializeObject = function () {
                var o = {};
                var a = this.serializeArray();
                $.each(a, function () {
                    if (o[this.name] !== undefined) {
                        if (!o[this.name].push) {
                            o[this.name] = [o[this.name]];
                        }
                        o[this.name].push(this.value || '');
                    } else {
                        o[this.name] = this.value || '';
                    }
                });
                return o;
            };

        }); // end document ready


        // removes an entry from the add invoice list.
        function removeBill(bill) {

            var path = "<c:out value="${ oscar_context_path }" />/methadoneBillingBC.do";
            var data = "?remove=" + bill;

            $("#methadoneBillingForm").attr("action", path + data);
            $("#methadoneBillingForm").submit();

        }

    </script>

</head>
<body id="methadoneBilling">

<div id="heading" class="billingHeading">
    <h1>Oscar by Treatment</h1>
    <h2>BC Methadone Billing</h2>
</div>

<form action="<c:out value="${oscar_context_path}" />/methadoneBillingBC.do"
      id="methadoneBillingForm"
      name="methadoneBillingForm"
      method="GET"
      class="bgLightLilac">
    <input type="hidden" name="type" value="<%= request.getParameter("type") %>"/>
    <input type="hidden" name="status" value="<%= request.getParameter("status") %>"/>
    <div id="header" class="bgLilac">
        <ul>
            <li>
                <fmt:setBundle basename="oscarResources"/><fmt:message key="billing.provider.billProvider"/>

                <select id="billingProviderNo" name="billingProviderNo">
                    <option value="empty">- Select Provider -</option>
                    <c:forEach var="provider" items="${ methadoneBillingBC.providerList }">

                        <c:if test="${not empty provider.ohipNo}">

                            <option value="<c:out value="${ provider.providerNo}" />"
                                    id="<c:out value="${ provider.providerNo }" />"
                                    <c:if test="${provider.providerNo eq methadoneBillingBC.billingProviderNo}">
                                        selected="selected"
                                    </c:if> >

                                <c:out value="${ provider.fullName }"/>
                            </option>

                        </c:if>
                    </c:forEach>
                </select>
            </li>
            <li>


                Roster
                <input type="text" name="rosterStatus" value="<c:out value="${methadoneBillingBC.rosterStatus}" />">
            </li>

            <li>
                <!--   fmt:message key="billing.providers.billProvider"/ -->

                Status
                <input type="text" name="patientStatus" value="<c:out value="${methadoneBillingBC.patientStatus}" />">
            </li>

            <li>
                Date of Service
                <input type="text"
                       id="serviceDate"
                       name="serviceDate"
                       size="10"
                       maxlength="10"
                       value="<c:out value="${methadoneBillingBC.serviceDate}" />"/>
            </li>
            <li>
                <input type="button" id="submitSearch" name="submitSearch" value="Search"
                       onclick="javascript:form.submit();"/>
            </li>
        </ul>
    </div>
</form>
<div id="errors">
    <% 
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
<% } %>
</div>
<form action="<c:out value="${oscar_context_path}" />/saveMethadoneBillingBC.do"
      id="saveMethadoneBillingForm"
      name="saveMethadoneBillingForm"
      method="POST"
      class="bgLightLilac">
    <input type="hidden" name="type" value="<%= request.getParameter("type") %>"/>
    <input type="hidden" name="status" value="<%= request.getParameter("status") %>"/>

    <div id="saved" style="display:none;">
        <c:out value="${requestScope.saved}"/> Invoice(s) Saved
    </div>

    <div id="inputList">
        <table>
            <tr>
                <th colspan="6">

                    <span id="tableDate"><c:out value="${methadoneBillingBC.serviceDate}"/></span>
                    <span id="tableName"><c:out value="${methadoneBillingBC.billingProvider}"/></span>

                </th>
            </tr>

            <tr class="bgLilac">
                <th>HIN</th>
                <th>Pt.Name</th>
                <th>Service</th>
                <th>DX Code</th>
                <th>Total</th>
                <th></th>
                <th></th>
            </tr>

            <c:forEach var="billData" items="${methadoneBillingBC.billingData}" varStatus="loop">

                <c:set value="noErrorStyle" var="classStyle"/>

                <%--
                    Check each value for errors
                    Errors are still displayed for review. The styling is changed to
                    indicate a possible error.
                --%>

                <c:choose>
                    <c:when test="${empty billData.patientPHN}">
                        <c:set value="HIN not found!" var="hin" scope="page"/>
                        <c:set var="classStyle" value="errorStyle"/>
                    </c:when>
                    <c:otherwise>
                        <c:set value="${billData.patientPHN}" var="hin" scope="page"/>
                        <c:set var="classStyle" value="noErrorStyle"/>
                    </c:otherwise>
                </c:choose>

                <tr>
                    <td class="<c:out value="${classStyle}" />">
                        <c:out value="${pageScope.hin}"/>
                    </td>
                    <td>
                        <c:out value="${billData.patientLastName}"/>,
                        <c:out value="${billData.patientFirstName}"/>
                    </td>

                    <c:forEach var="billItem" items="${billData.billItem}">

                        <c:choose>
                            <c:when test="${empty billItem.description}">
                                <c:set value="Billing Code Not Found" var="servicecode" scope="page"/>
                                <c:set var="classStyle" value="errorStyle"/>
                            </c:when>
                            <c:otherwise>
                                <c:set value="${billItem.description}" var="servicecode" scope="page"/>
                                <c:set var="classStyle" value="noErrorStyle"/>
                            </c:otherwise>
                        </c:choose>

                        <td class="<c:out value="${classStyle}" />">

                            <c:out value="${billItem.serviceCode}"/>:
                            <c:out value="${pageScope.servicecode}"/>

                        </td>

                        <c:choose>
                            <c:when test="${empty billData.dx1}">
                                <c:set value="DX Code Not Found" var="dxcode" scope="page"/>
                                <c:set var="classStyle" value="errorStyle"/>
                            </c:when>
                            <c:otherwise>
                                <c:set value="${billData.dx1}" var="dxcode" scope="page"/>
                                <c:set var="classStyle" value="noErrorStyle"/>
                            </c:otherwise>
                        </c:choose>
                        <td class="<c:out value="${classStyle}" />">
                            <c:out value="${pageScope.dxcode}"/>
                        </td>
                    </c:forEach>

                    <c:choose>
                        <c:when test="${billData.grandtotal <= '0.00'}">
                            <c:set var="classStyle" value="errorStyle"/>
                        </c:when>
                        <c:otherwise>
                            <c:set var="classStyle" value="noErrorStyle"/>
                        </c:otherwise>
                    </c:choose>

                    <td class="<c:out value="${classStyle}" />">

                        <fmt:formatNumber type="currency"
                                          groupingUsed="true"
                                          minFractionDigits="2"
                                          currencySymbol="$"
                                          value="${billData.grandtotal}"/>

                    </td>

                    <td>

                        <a id="removeBill"
                           href="<c:out value="${ oscar_context_path }" />/methadoneBillingBC.do?remove=<c:out value="${loop.index}" />&type=<%= request.getParameter("type") %>&status=<%= request.getParameter("status") %>">
                            remove
                        </a>

                    </td>
                    <td>
                        <c:out value="${ billItem.status }"/>
                    </td>
                </tr>

            </c:forEach>
        </table>
    </div>
    </div>
    <div id="toolBar" class="bgLilac">
        <input type="button" id="submitList" name="submitList" value="Submit" onclick="javascript:form.submit();"/>
        <input type="button" id="cancelTrans" name="cancelTrans" value="Cancel" onclick="javascript:window.close();"/>
    </div>
</form>
</body>
</html>
