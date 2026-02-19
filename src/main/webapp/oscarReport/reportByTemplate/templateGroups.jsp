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

<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>


<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>

<!DOCTYPE html>
<html>
    <head>
        <title>Report by Template Groups</title>

        <link href="${pageContext.request.contextPath}/css/bootstrap.css" rel="stylesheet">
        <link href="${pageContext.request.contextPath}/css/DT_bootstrap.css" rel="stylesheet">
        <link href="${pageContext.request.contextPath}/css/bootstrap-responsive.css" rel="stylesheet">
        <link href="${pageContext.request.contextPath}/library/jquery/jquery-ui.theme-1.12.1.min.css" rel="stylesheet">
        <link href="${pageContext.request.contextPath}/library/jquery/jquery-ui.structure-1.12.1.min.css"
              rel="stylesheet">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/fontawesome-all.min.css">

        <script src="${pageContext.servletContext.contextPath}/library/jquery/jquery-1.12.0.min.js"></script>

        <script src="${pageContext.request.contextPath}/library/jquery/jquery-ui-1.12.1.min.js"></script>

        <script src="${pageContext.request.contextPath}/js/bootstrap.min.2.js"></script>


    </head>

    <body>

    <%@ include file="rbtTopNav.jspf" %>

    <h3>Template Groups</h3>

    <c:choose>
        <c:when test="${ empty templatesInGroup }">
            <div class="row-fluid">
                <div class="well span12" id=manageGroups>

                    <div class="row-fluid">
                        <!--ADD GROUP-->
                        <form action="${pageContext.request.contextPath}/oscarReport/reportByTemplate/actions/addGroup.do"
                              method="post" id="addGroupTemplate" class="form-inline">
                            <input type="text" name="groupName" class="check" placeholder="Group Name">
                            <input type="submit" name="subm" class="btn groupAdd" value="Add Group" disabled>
                        </form>

                        <div class="alert alert-error textExists" style="display:none;">
                            <button type="button" class="close" data-dismiss="alert">&times;</button>
                            <strong>Error!</strong> the group name you selected already exists.
                        </div>
                    </div>
                    <div class="row-fluid">
                        <!--GROUP LIST-->
                        <table class="table table-condensed table-striped" id="groupListTbl">
                            <thead>
                            <tr>
                                <th colspan="2">Group Name</th>
                            </tr>
                            </thead>

                            <tbody>
                            <c:choose>
                                <c:when test="${ not empty rbtGroups }">
                                    <c:forEach items="${ rbtGroups }" var="groupName">
                                        <tr class="">
                                            <td title="${ groupName }">
                                                <a href="${pageContext.request.contextPath}/oscarReport/reportByTemplate/actions/tempInGroup.do?groupName=${ groupName }">
                                                    <c:out value="${ groupName }"/>
                                                </a>
                                            </td>
                                            <td>
                                                <form method="post" action="${pageContext.request.contextPath}/oscarReport/reportByTemplate/actions/delGroup.do" style="display:inline;">
                                                    <input type="hidden" name="groupName" value="${ groupName }"/>
                                                    <a class="pull-right" href="javascript:void(0);"
                                                       onclick="if(confirm('Are you sure you want to delete this group?')){this.closest('form').submit();}"
                                                       title="delete group">
                                                        <i style="color:red;" class="fa-solid fa-xmark"></i>
                                                    </a>
                                                </form>
                                                <span>&nbsp;</span>
                                                <a class="pull-right"
                                                   href="${pageContext.request.contextPath}/oscarReport/reportByTemplate/actions/tempInGroup.do?groupName=${ groupName }"
                                                   title="edit group">
                                                    <i style="color:blue;" class="fa-solid fa-pen-to-square"></i>
                                                </a>
                                            </td>
                                        </tr>
                                    </c:forEach>
                                </c:when>
                                <c:otherwise>
                                    <tr>
                                        <td></td>
                                        <td>No template groups created. Add a group to proceed.</td>
                                    </tr>
                                </c:otherwise>
                            </c:choose>
                            </tbody>
                        </table>
                    </div>
                </div>
            </div>
        </c:when>
        <c:otherwise>
            <div class="row-fluid">
                <!--TEMPLATES IN GROUP-->
                <div class="well span12">
                    <div class="row-fluid">
                        <c:choose>
                            <c:when test="${ not empty templatesInGroup }">
                                <h4>Templates in Group: <c:out value="${templatesInGroup[0].groupName}"/></h4>
                            </c:when>
                            <c:otherwise>
                                <h4>Templates in Group:</h4>
                            </c:otherwise>
                        </c:choose>
                    </div>
                    <div class="row-fluid">
                        <table class="table table-condensed table-striped" id="groupData">
                            <thead>
                            <tr>
                                <th>
                                    <a href="#" class="contentLink">
                                        Template Name
                                    </a>
                                </th>

                                <th colspan="2">
                                    <a href="#" class="contentLink">
                                        Description
                                    </a>
                                </th>
                            </tr>
                            </thead>

                            <tbody>
                            <c:choose>
                                <c:when test="${ templatesInGroup.size() gt 1 }">
                                    <c:forEach items="${ templatesInGroup }" var="temp">
                                        <c:set var="template" value="${ temp.templateId }"/>
                                        <tr class="">
                                            <c:choose>
                                                <c:when test="${ not empty templates }">

                                                    <c:if test="${ template ne '0'}">
                                                        <td title="">
                                                            <a href="${pageContext.request.contextPath}/oscarReport/reportByTemplate/reportConfiguration.jsp?templateid=${ template }"
                                                               class="contentLink"> <c:out
                                                                    value="${ templates[template].title }"/> </a>
                                                        </td>
                                                        <td>
                                                            <c:out value="${ templates[template].description }"/>
                                                        </td>

                                                        <td>
                                                            <form method="post" action="${pageContext.request.contextPath}/oscarReport/reportByTemplate/actions/remFromGroup.do" style="display:inline;">
                                                                <input type="hidden" name="tid" value="${template}"/>
                                                                <input type="hidden" name="groupName" value="${temp.groupName}"/>
                                                                <a href="javascript:void(0);"
                                                                   onclick="if(confirm('Remove template from group?')){this.closest('form').submit();}"
                                                                   class="pull-right" title="delete template from group">
                                                                    <i style="color:red;" class="fa-solid fa-xmark"></i>
                                                                </a>
                                                            </form>
                                                        </td>
                                                    </c:if>

                                                </c:when>
                                                <c:otherwise>
                                                    <td title="">
                                                        Error in retrieving template.
                                                    </td>

                                                    <td colspan="2">
                                                        The template may have been deleted.
                                                    </td>
                                                </c:otherwise>
                                            </c:choose>
                                        </tr>
                                    </c:forEach>
                                </c:when>
                                <c:otherwise>
                                    <tr>
                                        <td>No templates in this group</td>
                                        <td colspan="2">Click "Select Templates"</td>
                                    </tr>

                                </c:otherwise>
                            </c:choose>
                            </tbody>
                        </table>
                    </div>
                    <div class="row-fluid actions">
                        <form class="form-inline"
                              action="${pageContext.request.contextPath}/oscarReport/reportByTemplate/rbtGroup.do"
                              id="goBack">
                            <button type="submit" name="back-btn" id="back-btn"
                                    title="return to template group page" class="btn btn-secondary pull-right">Back
                            </button>

                            <button type="button" style="margin-right:5px;" name="selectRbtTemplatesBtn"
                                    id="selectRbtTemplatesBtn"
                                    title="add template to this group" data-toggle="modal"
                                    data-target="#selectTemplatesModal" class="btn btn-primary pull-right">
                                Select Templates
                            </button>

                        </form>
                    </div>
                </div>
            </div>
        </c:otherwise>
    </c:choose>

    <!-- MODAL for template group selection -->
    <div id="selectTemplatesModal" class="modal hide" tabindex="-1"
         aria-labelledby="selectTemplatesModal" aria-hidden="true">
        <div class="modal-dialog">
            <div class="modal-content" role="dialog">
                <div class="modal-header">
                    <button class="close" data-dismiss="modal">
                        &times;
                    </button>
                    <h3 class="modal-title">Select templates for group: <c:out
                            value="${templatesInGroup[0].groupName}"/></h3>
                </div>
                <div class="modal-body">
                    <form action="${pageContext.request.contextPath}/oscarReport/reportByTemplate/actions/rbtAddToGroup.do"
                          method="post" id="templateToGroupForm">
                        <div>
                            <select style="width:100%;height:400px;outline:none;" name="tid" id="templateSelect"
                                    multiple>
                                <c:if test="${ not empty templatesNotInGroup}">
                                    <c:forEach var="template" items="${ templatesNotInGroup }">
                                        <option value="${template.templateId}"><c:out
                                                value="${template.title}"/></option>
                                    </c:forEach>
                                </c:if>
                            </select>
                            <input type="hidden" name="groupName" value="${templatesInGroup[0].groupName}">
                        </div>
                    </form>
                </div>

                <div class="modal-footer">
                    <button type="button" class="btn btn-secondary" data-dismiss="modal">Cancel</button>
                    <button type="button" id="templateToGroup-btn" class="btn btn-primary">
                        Add Selected Template(s) to ${templatesInGroup[0].groupName}
                    </button>
                </div>
            </div>
        </div>
    </div>

    <script type="text/javascript">

        $(document).ready(function () {

            $("#templateToGroup-btn").on("click", function () {
                $("#templateToGroupForm").trigger("submit");
                $('#selectTemplatesModal').modal('hide');
            });

            $(".check").on("change", validate).keyup(validate);

        });


        // Function ensures that no duplicate groups can be added
        // Variable v - user entered group name to be compared
        function validate() {
            var value = $(this).val();
            value = value.trim();
            var id = $(this).attr("id");
            var inputCheck = checkRow(value);

            if (value != "" && inputCheck == "") {
                $('.groupAdd').removeAttr("disabled");
                $('.groupAdd').addClass("btn-success");
                $('.textExists').hide();
            } else if (inputCheck == "exists") {
                $('.groupAdd').attr("disabled", "disabled");
                $('.groupAdd').removeClass("btn-success");
                $('.textExists').show();
            } else {
                $('.groupAdd').attr("disabled", "disabled");
                $('.groupAdd').removeClass("btn-success");
                $('.textExists').hide();
            }
        }

        function checkRow(textInput) {
            var result = "";
            $('#groupListTbl tbody').find('tr').each(function () {

                if ($("td:nth(0)", $(this)).attr("title") && $("td:nth(0)", $(this)).attr("title").trim().toUpperCase() === textInput.toUpperCase()) {
                    result = "exists";
                    return false;
                }
            });

            return result
        }

    </script>
    </body>
</html>