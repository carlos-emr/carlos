<%--

    Copyright (c) 2008-2012 Indivica Inc.

    This software is made available under the terms of the
    GNU General Public License, Version 2, 1991 (GPLv2).
    License details are available via "indivica.ca/gplv2"
    and "gnu.org/licenses/gpl-2.0.html".

--%>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_admin,_admin.misc" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_admin&type=_admin.misc");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@page import="java.util.*, ca.openosp.openo.hospitalReportManager.*, ca.openosp.openo.hospitalReportManager.model.HRMCategory" %>
<%@ page import="ca.openosp.openo.hospitalReportManager.HRMUtil" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<!DOCTYPE html >
<html>
    <head>
        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>Show Mappings</title>
        <link rel="stylesheet" type="text/css"
              href="${ pageContext.request.contextPath }/library/bootstrap/3.0.0/css/bootstrap.min.css">
        <link rel="stylesheet"
              href="${ pageContext.request.contextPath }/library/DataTables-1.10.12/media/css/jquery.dataTables.min.css">

        <script src="${ pageContext.request.contextPath }/library/jquery/jquery-3.6.4.min.js"></script>
        <script src="${ pageContext.request.contextPath }/library/DataTables/datatables.min.js"></script>
        <!-- DataTables 1.13.4 -->

        <script>
            jQuery(document).ready(function () {
                jQuery('#tblMap').DataTable({
                    "order": [],
                    "bPaginate": false,
                    "language": {
                        "url": "<%=request.getContextPath() %>/library/DataTables/i18n/<fmt:setBundle basename="oscarResources"/><fmt:message key="global.i18nLanguagecode"/>.json"
                    }
                });
            });
        </script>

    </head>

    <body onunload="updateAjax()" class="BodyStyle">
    <div class="table-responsive">

        <div class="col-sm-12">
            <h4>Show Mappings</h4>
            <p class="pull-right">
                <a href="javascript:popupStart(300,400,'Help.jsp')"><fmt:setBundle basename="oscarResources"/><fmt:message key="global.help"/></a> |
                <a href="javascript:popupStart(300,400,'About.jsp')"><fmt:setBundle basename="oscarResources"/><fmt:message key="global.about"/></a> |
                <a href="javascript:popupStart(300,400,'License.jsp')"><fmt:setBundle basename="oscarResources"/><fmt:message key="global.license"/></a>
            </p>
            <div>
                <% if (request.getAttribute("success") != null) {
                    if ((Boolean) request.getAttribute("success")) {
                %>
                Successfully added the mapping
                <% } else { %>
                Error encountered while adding the mapping<br/>
                <%
                        }
                    }
                %>
            </div>
            <div>
                <a href="<%=request.getContextPath() %>/hospitalReportManager/hrmAddClassMapping.jsp">+ Add a class
                    mapping</a>
            </div>
            <hr/>
            <table id="tblMap" class="table table-striped table-hover table-condensed">
                <thead>
                <tr>
                    <th>Sending Facility Id</th>
                    <th>Class Name</th>
                    <th>SubClass Name Mnemonic</th>
                    <th>Mnemonic</th>
                    <th>Description</th>
                    <th>Category</th>
                    <th></th>
                </tr>
                </thead>
                <tbody>
                <%
                    ArrayList<HashMap<String, ? extends Object>> hrmmappings = HRMUtil.listMappings();
                    for (int i = 0; i < hrmmappings.size(); i++) {
                        HashMap<String, ? extends Object> curmapping = hrmmappings.get(i);
                %>
                <tr>
                    <td><%=curmapping.get("id")%>&nbsp;</td>
                    <td><%=curmapping.get("class")%>&nbsp;</td>
                    <td><%=curmapping.get("sub_class")%>&nbsp;</td>
                    <td><%=curmapping.get("mnemonic") %>&nbsp;</td>
                    <td><%=curmapping.get("description") %>&nbsp;</td>
                    <td><%=((HRMCategory) curmapping.get("category")).getCategoryName() %>&nbsp;</td>
                    <td>
                        <a href="<%=request.getContextPath() %>/hospitalReportManager/Mapping.do?deleteMappingId=<%=curmapping.get("mappingId") %>">Delete</a>
                    </td>
                </tr>
                <%
                    }
                %>
                </tbody>
            </table>
        </div>
    </div>
    </body>
</html>