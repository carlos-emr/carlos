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

<%@page import="org.apache.commons.text.StringEscapeUtils" %>
<%@page import="ca.openosp.openo.hospitalReportManager.model.HRMCategory" %>
<%@page import="ca.openosp.openo.utility.SpringUtils" %>
<%@page import="ca.openosp.openo.hospitalReportManager.dao.HRMCategoryDao" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%
    HRMCategoryDao hrmCategoryDao = SpringUtils.getBean(HRMCategoryDao.class);
    String id = request.getParameter("id");
    HRMCategory existingCategory = null;
    if (id != null) {
        existingCategory = hrmCategoryDao.find(Integer.parseInt(id));
    }
%>
<!DOCTYPE html >
<html>
    <head>
        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>HRM Categories</title>
        <link rel="stylesheet" type="text/css"
              href="${ pageContext.request.contextPath }/library/bootstrap/3.0.0/css/bootstrap.min.css">
        <link rel="stylesheet"
              href="${ pageContext.request.contextPath }/library/DataTables-1.10.12/media/css/jquery.dataTables.min.css">

        <script src="<%=request.getContextPath() %>/library/jquery/jquery-3.6.4.min.js"></script>
        <script src="<%=request.getContextPath() %>/library/DataTables/datatables.min.js"></script>
        <!-- DataTables 1.13.4 -->

        <script>
            jQuery(document).ready(function () {
                jQuery('#tblCategory').DataTable({
                    "order": [],
                    "bPaginate": false,
                    "language": {
                        "url": "<%=request.getContextPath() %>/library/DataTables/i18n/<fmt:setBundle basename="oscarResources"/><fmt:message key="global.i18nLanguagecode"/>.json"
                    }
                });
            });
        </script>

    </head>
    <body>
    <div class="table-responsive">

        <div class="col-sm-12">
            <h4>HRM Categories</h4>

            <form method="post" action="hrm_categories_action.jsp">
                <input type="hidden" name="action" value="add"/>
                <input type="hidden" name="id" value="<%=existingCategory != null ? existingCategory.getId() : ""%>"/>
                <fieldset>
                    <div class="control-group">
                        <label class="control-label">Category Name:</label>
                        <div class="controls">
                            <input type="text" name="categoryName" class="form-control input-normal"
                                   value="<%=existingCategory != null ? existingCategory.getCategoryName() : ""  %>"/>
                        </div>
                    </div>
                    <div class="w-100"></div>
                    <div class="control-group">
                        <label class="control-label">SubClass Name Mnemonic:</label>
                        <div class="controls">
                            <input type="text" name="subClassNameMnemonic" class="form-control"
                                   value="<%=existingCategory != null ? existingCategory.getSubClassNameMnemonic() : ""  %>"/>
                            (should be of the format &lt;subclass_name&gt;:&lt;subclass_mnemonic&gt;)
                        </div>
                    </div>
                    <div class="control-group">
                        <input type="submit" class="btn btn-primary"
                               value="<%=existingCategory != null ? "Save" : "Add" %>"/>
                    </div>
                </fieldset>
            </form>
            <hr/>
            <table id="tblCategory" class="table table-striped table-hover table-condensed">
                <thead>
                <tr>
                    <td></td>
                    <th>ID</th>
                    <th>Category Name</th>
                    <th>SubClass Name Mnemonic</th>
                </tr>
                </thead>
                <tbody>
                <%
                    for (HRMCategory category : hrmCategoryDao.findAll()) {
                %>
                <tr>
                    <td><a href="hrm_categories_action.jsp?action=delete&id=<%=category.getId()%>"><img
                            src="<%=request.getContextPath()%>/images/icons/101.png" alt="alert"></a></td>
                    <td><a href="hrmCategories.jsp?id=<%=category.getId()%>"><%=category.getId()%>
                    </a></td>
                    <td><%=StringEscapeUtils.escapeHtml4(category.getCategoryName())%>&nbsp;</td>
                    <td><%=StringEscapeUtils.escapeHtml4(category.getSubClassNameMnemonic())%>&nbsp;</td>
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