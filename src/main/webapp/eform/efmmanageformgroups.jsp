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
<%@page import="java.net.URLEncoder" %>
<%@ page import="ca.openosp.openo.eform.data.*, ca.openosp.openo.eform.*, java.util.*" %>
<%@ page import="ca.openosp.openo.eform.EFormUtil" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%
    String user = (String) session.getAttribute("user");
    if (session.getAttribute("userrole") == null) response.sendRedirect(request.getContextPath() + "/logout.jsp");
    String roleName = (String) session.getAttribute("userrole") + "," + user;

    ArrayList groups = EFormUtil.getEFormGroups();
    ArrayList<HashMap<String, ? extends Object>> forms = EFormUtil.listEForms(EFormUtil.NAME, EFormUtil.CURRENT);
    String groupView = request.getParameter("group_view");
    if (groupView == null) {
        groupView = (String) request.getAttribute("group_view");
    }
    if (groupView == null) {
        if (groups.size() > 0) {
            HashMap tmphash = (HashMap) groups.get(0);
            groupView = (String) tmphash.get("groupName");
        } else {
            groupView = "";
        }
    }

    String orderByRequest = request.getParameter("orderby");
    String orderBy = "";
    if (orderByRequest == null) orderBy = EFormUtil.DATE;
    else if (orderByRequest.equals("form_subject")) orderBy = EFormUtil.SUBJECT;
    else if (orderByRequest.equals("form_name")) orderBy = EFormUtil.NAME;
    else if (orderByRequest.equals("file_name")) orderBy = EFormUtil.FILE_NAME;
%>
<!DOCTYPE html>
<html>
    <head>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.frmGroups"/></title>

        <style>
            .contentLink {
                display: block;
                width: 100%;
            }

            #eformSelect {
                width: 400px;
                font-size: 18px;
                padding: 2px;
            }

        </style>


    </head>

    <body>
    <%@ include file="efmTopNav.jspf" %>

    <h3><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.frmGroups"/></h3>
    <div class="row-fluid">
        <div class="well span6">

            <!--ADD GROUP-->
            <form action="<%= request.getContextPath() %>/eform/addGroup.do" method="get" id="addGroupForm"
                  class="form-inline">
                <div class="controls">
                    <div class="input-append">
                        <input type="text" name="groupName" class="check"
                               placeholder="<fmt:setBundle basename="oscarResources"/><fmt:message key="eform.groups.addGroup"/>">
                        <input type="submit" name="subm" class="btn btn-primary groupAdd"
                               value="<fmt:setBundle basename="oscarResources"/><fmt:message key="eform.groups.addGroup"/>" disabled>
                    </div>
                </div>
            </form>


            <div class="alert alert-error textExists" style="display:none;">
                <button type="button" class="close" data-dismiss="alert">&times;</button>
                <strong>Error!</strong> the group name you selected already exists.
            </div>

            <!--GROUP LIST-->
            <table class="table table-condensed table-striped" id="groupListTbl">
                <thead>
                <tr>
                    <th></th>
                    <th>Group Name</th>
                    <th>eForms</th>
                </tr>
                </thead>

                <tbody>
                <%

                    String groupName = "";

                    for (int i = 0; i < groups.size(); i++) {
                        HashMap curhash = (HashMap) groups.get(i);
                        groupName = curhash.get("groupName").toString();

                        if (groupName.equals(groupView)) {
                %>
                <tr class="success">
                            <%}else{%>
                <tr>
                    <%}%>
                    <td>
                        <a href='<%= request.getContextPath() %>/eforms/delGroup.do?group_name=<%=URLEncoder.encode(groupName, "UTF-8")%>'
                           class="btn btn-small" title="delete this group"
                           data-confirm="<i class='icon-warning-sign icon-large'></i> Are you sure you would like to delete group: <strong><%=groupName%></strong>?"><i
                                class="icon-trash"></i></a></td>
                    <td title="<%=groupName%>"><a
                            href='<%= request.getContextPath() %>/eform/efmmanageformgroups.jsp?orderby=form_name&group_view=<%=URLEncoder.encode(groupName, "UTF-8")%>'
                            class="contentLink"><%=groupName%>
                    </a></td>
                    <td><%= (String) curhash.get("count") %>
                    </td>

                </tr>

                <% } %>
                </tbody>
            </table>
        </div>


        <!--EFORMS IN GROUP-->

        <div class="well span6">
            <h4><fmt:setBundle basename="oscarResources"/><fmt:message key="eform.groups.contents"/>: <%=groupView%>
            </h4>

            <table class="table table-condensed table-striped">
                <thead>
                <tr>
                    <th>
                    </th>

                    <th>
                        <a href="<%= request.getContextPath() %>/eform/efmmanageformgroups.jsp?orderby=form_name&group_view=<%=groupView%>"
                           class="contentLink">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="eform.uploadhtml.btnFormName"/>
                        </a>
                    </th>

                    <th>
                        <a href="<%= request.getContextPath() %>/eform/efmmanageformgroups.jsp?group_view=<%=groupView%>"
                           class="contentLink">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="eform.uploadhtml.btnDate"/>
                        </a>
                    </th>

                </tr>
                </thead>

                <tbody>
                <%
                    if (!groupView.equals("")) {
                        ArrayList<HashMap<String, ? extends Object>> eForms = EFormUtil.listEForms(orderBy, EFormUtil.CURRENT, groupView, roleName);
                        if (eForms.size() > 0) {
                            for (int i = 0; i < eForms.size(); i++) {
                                HashMap<String, ? extends Object> curForm = eForms.get(i);
                %>
                <tr rel="popover" data-html="true" data-title="<%=curForm.get("formName")%>"
                    data-content="<strong><fmt:setBundle basename="oscarResources"/><fmt:message key="eform.uploadhtml.btnSubject"/>:</strong><br> <%=curForm.get("formSubject")%> <br> <small><fmt:setBundle basename="oscarResources"/><fmt:message key="eform.uploadhtml.btnFile"/>: <%=curForm.get("formFileName")%></small>"
                    data-trigger="hover" data-placement="bottom">

                    <td>
                        <a href="<%= request.getContextPath() %>/eforms/removeFromGroup.do?fid=<%=curForm.get("fid")%>&groupName=<%=URLEncoder.encode(groupView, "UTF-8")%>"
                           title="remove from group" class="btn btn-small" title="delete eform from group"
                           data-confirm="<i class='icon-warning-sign icon-large'></i> Are you sure you would like to remove this eform from this group?"><i
                                class="icon-trash"></i></a>
                    </td>

                    <td><a href="#"
                           onclick="newWindow('<%= request.getContextPath() %>/eform/efmshowform_data.jsp?fid=<%=curForm.get("fid")%>', '<%="FormG" + i%>'); return false;"><%=curForm.get("formName")%>
                    </a></td>

                    <td align='center'><%=curForm.get("formDate")%>
                    </td>


                </tr>
                <% }
                } else { %>
                <tr align="center">
                    <td colspan="5"><fmt:setBundle basename="oscarResources"/><fmt:message key="eform.groups.noFormsInGroup"/></td>
                </tr>
                <% }
                } else {%>
                <tr align="center">
                    <td colspan="5"><fmt:setBundle basename="oscarResources"/><fmt:message key="eform.groups.noGroupMsg"/></td>
                </tr>
                <% } %>
                </tbody>
            </table>

            <div class="row-fluid">
                <button type="button" name="addEform-btn" id="addEform-btn" title="add eform to this group"
                        class="btn btn-primary modalShow pull-right">Add eForm
                </button>
            </div>
        </div>
        <!--modal-->
                <% if (!groupView.equals("")) { %>
        <form action="${pageContext.request.contextPath}/eform/addToGroup.do" method="get" id="eformToGroupForm">
        <div id="myModal" class="modal hide fade" tabindex="-1" role="dialog" aria-labelledby="myModalLabel"
             aria-hidden="true">
            <div class="modal-header">
                <button type="button" class="close" data-dismiss="modal" aria-hidden="true">X</button>
                <h3 id="myModalLabel"><fmt:setBundle basename="oscarResources"/><fmt:message key="eform.groups.addToGroup"/> <%=groupView%>
                </h3>
            </div>
            <div class="modal-body">

                <div style="text-align:center">

                    <select name="fid" id="eformSelect">
                        <%
                            for (int i = 0; i < forms.size(); i++) {
                                HashMap<String, ? extends Object> curhash = forms.get(i);
                        %>
                        <option
                                value='<%= (String) curhash.get("fid")%>'><%= (String) curhash.get("formName")%> | <%= (String) curhash.get("formDate")%>
                        </option>
                        <% } %>
                    </select>


                    <input type="hidden" name="groupName" value="<%= groupView%>">

                </div>


            </div>
            <div class="modal-footer">
                <button class="btn" data-dismiss="modal" aria-hidden="true">Cancel</button>

                <input type="submit" name="subm" id="eformToGroup-btn" class="btn btn-primary"
                       value="Add eForm to Group">
            </div>
        </div>
        </form>
                <% } %>

            <%@ include file="efmFooter.jspf" %>

        <script>
            registerFormSubmit('addGroupForm', 'dynamic-content');
            registerFormSubmit('eformToGroupForm', 'dynamic-content');


            $(function () {
                $("[rel=popover]").popover({});
            });

            $(document).ready(function () {

                $("#eformToGroupForm").submit(function () {
                    $('#myModal').modal('hide');
                });

                $(".modalShow").click(function () {
                    $('#myModal').modal('toggle');
                });

                $(".check").change(validate).keyup(validate);

            });

            function validate() {
                var v = $(this).val();
                var id = $(this).attr("id");

                var inputCheck = checkRow(v);

                if (v != "" && inputCheck == "") {
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

                    if ($('td:nth(1)', $(this)).attr("title") === textInput) {
                        result = "exists";
                        return false;
                    }
                });

                return result
            }
        </script>
    </body>
</html>
