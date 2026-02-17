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
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ page import="java.util.*" %>
<%@ page import="java.util.ResourceBundle"%>
<%
    if (session.getValue("user") == null)
        response.sendRedirect(request.getContextPath() + "/logout.htm");

    ResourceBundle bundle = ResourceBundle.getBundle("oscarResources", request.getLocale());

    String providertitle = (String) request.getAttribute("providertitle");
    String providermsgPrefs = (String) request.getAttribute("providermsgPrefs");
    String providermsgProvider = (String) request.getAttribute("providermsgProvider");
    String providermsgSuccess = (String) request.getAttribute("providermsgSuccess");
%>
<!DOCTYPE html>
<html lang="en">
    <head>
        <%@ include file="/includes/global-head.jspf" %>
        <title><%=bundle.getString(providertitle)%></title>
    </head>

    <body>
    <div class="container">

        <div class="page-header-bar">
            <h4 class="page-header-title">
                <i class="fas fa-redo page-header-icon"></i>&nbsp;<%=bundle.getString(providermsgProvider)%>
            </h4>
        </div>

        <%if (request.getAttribute("status") == null) {%>

        <form action="${pageContext.request.contextPath}/setProviderStaleDate.do" method="post">
            <input type="hidden" name="method" value="<c:out value="${method}"/>">

            <div class="row mb-3">
                <label class="col-sm-3 col-form-label fw-bold">
                    Delegate <span class="text-danger">*required</span>
                </label>
                <div class="col-sm-5">
                    <select name="labRecallDelegate.value" id="labRecallDelegate.value"
                            class="form-select form-select-sm" onchange="delegateCheck();">
                        <c:forEach var="provider" items="${providerSelect}">
                            <option value="${provider.value}" <c:if test="${provider.value == labRecallDelegate.value}">selected</c:if>>
                                ${provider.label}
                            </option>
                        </c:forEach>
                    </select>
                </div>
            </div>

            <div class="row mb-3">
                <label class="col-sm-3 col-form-label fw-bold">Default Message Subject</label>
                <div class="col-sm-5">
                    <input type="text" name="labRecallMsgSubject.value"
                           value="<c:out value='${subject.value}'/>"
                           class="form-control form-control-sm">
                </div>
            </div>

            <div class="row mb-3">
                <label class="col-sm-3 col-form-label fw-bold">Tickler Assignee</label>
                <div class="col-sm-5 d-flex align-items-center gap-2">
                    <input type="checkbox" class="form-check-input"
                           name="labRecallTicklerAssignee.checked"
                           <c:if test="${labRecallTicklerAssignee.checked}">checked</c:if>>
                    <span>Default to delegate</span>
                </div>
            </div>

            <div class="row mb-3">
                <label class="col-sm-3 col-form-label fw-bold">Tickler Priority</label>
                <div class="col-sm-5">
                    <select name="labRecallTicklerPriority.value" id="labRecallTicklerPriority.value"
                            class="form-select form-select-sm">
                        <c:forEach var="priority" items="${prioritySelect}">
                            <option value="${priority.value}" <c:if test="${priority.value == labRecallTicklerPriority.value}">selected</c:if>>
                                ${priority.label}
                            </option>
                        </c:forEach>
                    </select>
                </div>
            </div>

            <div class="d-flex gap-2">
                <input type="submit" name="btnApply" class="btn btn-primary btn-sm" value="Save">
                <input type="button" name="delete" class="btn btn-outline-danger btn-sm"
                       value="Delete" onclick="deleteProp();" style="display:none;">
            </div>

        </form>

        <%} else {%>
        <div class="alert alert-success"><%=bundle.getString(providermsgSuccess)%></div>
        <%}%>

    </div>

    <script>
        function deleteProp() {
            var r = confirm("Are you sure you would like to delete the lab recall settings?");
            if (r === true) {
                document.forms[0].reset();
                document.forms[0]['labRecallDelegate.value'].value = "";
                document.forms[0].submit();
            }
        }

        function delegateCheck() {
            var delegate = document.forms[0]['labRecallDelegate.value'].value;
            document.forms[0]['btnApply'].disabled = (delegate === "");
        }

        delegateCheck();

        if (document.forms[0] && document.forms[0]['labRecallDelegate.value'].value !== "") {
            document.forms[0]['delete'].style.display = "inline";
        }
    </script>
    </body>
</html>
