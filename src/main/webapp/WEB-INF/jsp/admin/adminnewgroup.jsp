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

<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="carlos" prefix="carlos" %>

<%@ page import="java.util.*" errorPage="/WEB-INF/jsp/error/errorpage.jsp" %>
<%@ page import="io.github.carlos_emr.carlos.admin.web.AdminNewGroup2Action" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.ProviderData" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.ProviderDataDao" %>

<%--
    Reached only through admin/AdminNewGroup (AdminNewGroup2Action), which checks the privilege,
    refuses a Delete that is not a POST, and performs the Delete of Search/Edit/Delete Groups
    before forwarding here. The page therefore never reads group members out of the request: it
    renders the Add a Group form, or the deletion notice when the action set
    AdminNewGroup2Action.MEMBERS_DELETED_ATTRIBUTE.
--%>
<%
    ProviderDataDao providerDao = SpringUtils.getBean(ProviderDataDao.class);
    boolean membersDeleted = request.getAttribute(AdminNewGroup2Action.MEMBERS_DELETED_ATTRIBUTE) != null;

    String curProvider_no = (String) session.getAttribute("user");
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");

    boolean isSiteAccessPrivacy = false;
%>

<security:oscarSec objectName="_site_access_privacy" roleName="<%=roleName$%>" rights="r" reverse="false">
    <%isSiteAccessPrivacy = true; %>
</security:oscarSec>

<!DOCTYPE html>
<html>
    <head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>

        <title><fmt:message key="admin.adminnewgroup.title"/></title>

        <script>

            function setfocus() {
                this.focus();
                // The deletion notice renders the same form without the group name field.
                var groupName = document.UPDATEPRE && document.UPDATEPRE.mygroup_no;
                if (groupName) {
                    groupName.focus();
                    groupName.select();
                }
            }

            function validate() {
                group = document.UPDATEPRE.mygroup_no.value;

                if (group.length <= 0 || group <= " ") {
                    alert("<fmt:message key="admin.adminNewGroup.msgGroupIsRequired"/>");

                    return false;
                } else {
                    var checked = false;
                    var checkboxes = document.getElementsByName("data");
                    var x = 0;
                    for (x = 0; x < checkboxes.length; x++) {
                        if (checkboxes[x].checked == true) {
                            checked = true;
                        }
                    }
                if (checked == false) {
                    alert("<fmt:message key='admin.adminnewgroup.msgChooseProviders'/>");
                    return false;
                }
                    return true;
                }
            }
        </script>

        <link href="<%=request.getContextPath() %>/library/bootstrap/5.3.8/css/bootstrap.min.css" rel="stylesheet">
    </head>


    <body onload="setfocus();">

    <FORM NAME="UPDATEPRE" METHOD="post" ACTION="${pageContext.request.contextPath}/admin/AdminSaveMyGroup" onsubmit="return validate();">

        <%if (membersDeleted) { %>
        <br>
        <div class="alert alert-success">
            <strong><fmt:message key="admin.adminnewgroup.msgSuccess"/></strong> <fmt:message key="admin.adminnewgroup.msgDeleteSuccess"/>
        </div>

        <a href="${pageContext.request.contextPath}/admin/ViewAdminDisplayMyGroup" class="btn btn-primary"><fmt:message key="admin.admindisplaymygroup.btnViewGroupList"/></a>

        <a href="${pageContext.request.contextPath}/admin/AdminNewGroup" class="btn btn-secondary"><fmt:message key="admin.admindisplaymygroup.btnSubmit2"/></a>
        <%} else {%>

        <h3><fmt:message key="admin.adminnewgroup.description"/></h3>


        <input type="text" name="mygroup_no" size="10" maxlength="10"
               placeholder="<fmt:message key="admin.adminmygroup.formGroupNo"/>"
               title="<fmt:message key='admin.adminnewgroup.titleGroupName'/>">
        <small><fmt:message key="admin.adminnewgroup.msgMaxChars"/></small>

        <table class="table table-sm table-hover">
            <thead>
            <tr class="btn-inverse">
                <th></th>
                <th>
                    <fmt:message key="admin.admindisplaymygroup.formProviderName"/>
                </th>
            </tr>
            </thead>

            <tbody>
            <%
                // find all active providers
                int i = 0;
                List<ProviderData> providerList = providerDao.findAll(false);

                for (ProviderData provider : providerList) {
                    i++;
            %>
            <tr class="<%=i%2==0?"":"table-info"%>">
                <td width="20px" ALIGN="center">
                    <input type="checkbox" name="data" value="<%=i%>">
                    <input type="hidden" name="provider_no<%=i%>" value="<carlos:encode value='<%= provider.getId() %>' context="htmlAttribute"/>">
                    <input type="hidden" name="last_name<%=i%>" value="<carlos:encode value='<%= provider.getLastName() %>' context="htmlAttribute"/>">
                    <input type="hidden" name="first_name<%=i%>" value="<carlos:encode value='<%= provider.getFirstName() %>' context="htmlAttribute"/>">
                </td>

                <td><carlos:encode value='<%= provider.getLastName() %>'/>, <carlos:encode value='<%= provider.getFirstName() %>'/>
                </td>

            </tr>
            <%
                }
            %>
            </tbody>
        </table>


        <input type="submit" name="Submit" class="btn btn-primary"
               value="<fmt:message key="admin.adminnewgroup.btnSubmit"/>">

        <a href="${pageContext.request.contextPath}/admin/ViewAdminDisplayMyGroup" class="btn btn-secondary"><fmt:message key="global.btnCancel"/></a>

    </FORM>

    <%} %>


    <script type="text/javascript" src="<%=request.getContextPath() %>/library/jquery/jquery-3.7.1.min.js"></script>
    <script src="<%=request.getContextPath() %>/library/jquery/jquery-compat.js"></script>

    <script>
        $(document).ready(function () {
            if (parent && parent.parent && typeof parent.parent.resizeIframe === 'function') {
                parent.parent.resizeIframe($('html').height());
            }

        });
    </script>
    </body>
</html>
