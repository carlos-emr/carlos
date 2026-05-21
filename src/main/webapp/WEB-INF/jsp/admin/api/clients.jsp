<%--
    Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
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

    CARLOS EMR Project
    https://github.com/carlos-emr/carlos
--%>
<%--
  Page role: Renders `clients.jsp` for the administration area.
  Keep request setup in the paired action and use CARLOS encoding helpers
  for dynamic output rendered by the page.
--%>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
"http://www.w3.org/TR/html4/loose.dtd">
<%-- This JSP is the first page you see when you enter 'report by template' --%>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>"
                   objectName="_admin" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_admin");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>


<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<html>
    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title><fmt:message key="admin.api.clients.title"/></title>
        <link href="<%=request.getContextPath() %>/library/bootstrap/5.3.8/css/bootstrap.min.css" rel="stylesheet" type="text/css">
        <link href="<%=request.getContextPath() %>/library/DataTables/DataTables-1.13.11/css/dataTables.bootstrap5.min.css" rel="stylesheet" type="text/css">
        <link rel="stylesheet" href="<%=request.getContextPath() %>/css/fontawesome-all.min.css">
        <link rel="stylesheet" href="<%=request.getContextPath()%>/library/jquery/jquery-ui-1.14.2.min.css">

        <script type="text/javascript" src="<%=request.getContextPath() %>/library/jquery/jquery-3.7.1.min.js"></script>
        <script src="<%=request.getContextPath() %>/library/jquery/jquery-compat.js"></script>
        <script type="text/javascript" src="<%=request.getContextPath() %>/library/jquery/jquery-ui-1.14.2.min.js"></script>
        <script type="text/javascript" src="<%=request.getContextPath() %>/library/bootstrap/5.3.8/js/bootstrap.bundle.min.js"></script>
        <script type="text/javascript" src="<%=request.getContextPath() %>/library/jquery/jquery.validate-1.21.0.min.js"></script>
        <script type="text/javascript" src="<%=request.getContextPath() %>/library/DataTables/DataTables-1.13.11/js/jquery.dataTables.min.js"></script>
        <script type="text/javascript" src="<%=request.getContextPath() %>/library/DataTables/DataTables-1.13.11/js/dataTables.bootstrap5.min.js"></script>
        <script type="text/javascript" language="JavaScript"
                src="<%= request.getContextPath() %>/share/javascript/Oscar.js"></script>

        <script>
            const apiClientsDeleteTitle = "<fmt:message key='admin.api.clients.deleteTitle'/>";
            const apiClientsAddClient = "<fmt:message key='admin.api.clients.addClient'/>";
            const apiClientsCancel = "<fmt:message key='admin.api.clients.cancel'/>";

            function addNewClient() {
                $('#new-form').dialog('open');
            }

            function listClients() {
                $("#clientTable tbody").find("tr").remove();
                jQuery.getJSON("<%= request.getContextPath() %>/admin/api/clientManage", {method: "list"},
                    function (data, textStatus) {
                        for (var x = 0; x < data.length; x++) {
                            var id = data[x].id;
                            var name = data[x].name;
                            var key = data[x].key;
                            var uri = data[x].uri;
                            var lifetime = data[x].lifetime;
                            var $row = $('<tr>');
                            $row.append($('<td>').text(name));
                            $row.append($('<td>').text(key));
                            $row.append($('<td>').text(uri));
                            $row.append($('<td>').text(lifetime));
                            var $delLink = $('<a>').attr('href', 'javascript:void(0);')
                                .on('click', (function(delId) { return function() { deleteClient(delId); }; })(id))
                                .append($('<img>').attr({border: '0', title: apiClientsDeleteTitle, src: '<%= request.getContextPath() %>/images/Delete16.gif'}));
                            $row.append($('<td>').append($delLink));
                            $('#clientTable > tbody:last').append($row);
                        }
                    });
            }

            function listTokens() {
                $("#tokenTable tbody").find("tr").remove();

                jQuery.getJSON("<%= request.getContextPath() %>/admin/api/clientManage", {method: "listTokens"},
                    function (data, textStatus) {


                        for (var x = 0; x < data.length; x++) {
                            var clientId = data[x].clientId;
                            var dateCreated = data[x].dateCreated;
                            var id = data[x].id;
                            var issued = data[x].issued;
                            var lifetime = data[x].lifetime;
                            var persistent = data[x].persistent;
                            var providerNo = data[x].providerNo;

                            var $trow = $('<tr>');
                            $trow.append($('<td>').text(id));
                            $trow.append($('<td>').text(lifetime));
                            $trow.append($('<td>').text(issued));
                            $trow.append($('<td>').text(providerNo));
                            $trow.append($('<td>'));
                            $('#tokenTable > tbody:last').append($trow);
                        }
                    });
            }


            function deleteClient(id) {
                jQuery.post("<%= request.getContextPath() %>/admin/api/clientManage", {
                        method: "delete",
                        id: id
                    },
                    function (xml) {
                        if (xml.success)
                            listClients();
                        else
                            alert(xml.error);
                    }, "json");
            }

            $(document).ready(function () {
                listClients();
                listTokens();

                $("#new-form").dialog({
                    autoOpen: false,
                    height: 400,
                    width: 450,
                    modal: true,
                    buttons: {
                        [apiClientsAddClient]: function () {
                            $(this).dialog("close");
                            var name = $("#clientName").val();
                            var uri = $("#clientURI").val();
                            var lifetime = $("#lifetime").val();
                            jQuery.post("<%= request.getContextPath() %>/admin/api/clientManage",
                                {
                                    method: "add",
                                    name: name,
                                    uri: uri,
                                    lifetime: lifetime
                                },
                                function (xml) {
                                    if (xml.success) {
                                        $("#clientName").val('');
                                        $("#clientURI").val('');
                                        $("#lifetime").val('');
                                        listClients();
                                    } else {
                                        alert(xml.error);
                                    }
                                }, "json");

                        },
                        [apiClientsCancel]: function () {
                            $(this).dialog("close");
                        }
                    },
                    close: function () {

                    }
                });

            });
        </script>
    </head>

    <body vlink="#0000FF" class="BodyStyle">
    <h4><fmt:message key="admin.api.clients.heading"/></h4>
    <table id="clientTable" name="clientTable" class="table table-bordered table-striped table-hover table-sm">
        <thead>
        <tr>
            <th><fmt:message key="admin.api.clients.table.name"/></th>
            <th><fmt:message key="admin.api.clients.table.clientKey"/></th>
            <th><fmt:message key="admin.api.clients.table.uri"/></td>
            <th><fmt:message key="admin.api.clients.table.tokenTtl"/></td>
            <th><fmt:message key="admin.api.clients.table.actions"/></th>
        </tr>
        </thead>
        <tbody></tbody>
    </table>
    <input type="button" class="btn btn-primary" value="<fmt:message key='admin.api.clients.addNew'/>" onClick="addNewClient()"/>
    <%
        String thisUrl = request.getRequestURL().toString();
        String contextPath = request.getContextPath();
        String here = thisUrl.substring(0, thisUrl.indexOf(contextPath) + contextPath.length());
    %>
    <hr/>
    <h4><fmt:message key="admin.api.clients.tokensHeading"/></h4>
    <table id="tokenTable" name="tokenTable" class="table table-bordered table-striped table-hover table-sm">
        <thead>
        <tr>
            <th><fmt:message key="admin.api.clients.table.id"/></th>
            <th><fmt:message key="admin.api.clients.table.ttlSeconds"/></th>
            <th><fmt:message key="admin.api.clients.table.issued"/></th>
            <th><fmt:message key="admin.api.clients.table.provider"/></td>
            <th><fmt:message key="admin.api.clients.table.actions"/></th>
        </tr>
        </thead>
        <tbody></tbody>
    </table>

    <hr/>
    <table class="table table-bordered table-striped table-hover table-sm">
        <tr>
            <td><fmt:message key="admin.api.clients.tempCredentialRequest"/></td>
            <td><carlos:encode value='<%= here %>' context="html"/>/ws/oauth/initiate</td>
        </tr>
        <tr>
            <td><fmt:message key="admin.api.clients.resourceOwnerAuthorizationUri"/></td>
            <td><carlos:encode value='<%= here %>' context="html"/>/ws/oauth/authorize</td>
        </tr>
        <tr>
            <td><fmt:message key="admin.api.clients.tokenRequestUri"/></td>
            <td><carlos:encode value='<%= here %>' context="html"/>/ws/oauth/token</td>
        </tr>
    </table>

    <div id="new-form" title="<fmt:message key='admin.api.clients.createClient'/>">
        <p class="validateTips"></p>
        <form>
            <fieldset>
                <div class="mb-3">
                    <label class="form-label" for="clientName"><fmt:message key="admin.api.clients.name"/>:</label>
                    <div>
                        <input type="text" name="clientName" id="clientName"/>
                    </div>
                </div>
                <div class="mb-3">
                    <label class="form-label" for="clientURI"><fmt:message key="admin.api.clients.uri"/>:</label>
                    <div>
                        <input type="text" name="clientURI" id="clientURI"/>
                    </div>
                </div>
                <div class="mb-3">
                    <label class="form-label" for="lifetime"><fmt:message key="admin.api.clients.tokenLifetime"/>:</label>
                    <div>
                        <input type="text" name="lifetime" id="lifetime"/>
                    </div>
                </div>
            </fieldset>
        </form>
    </div>
    </body>
</html>
