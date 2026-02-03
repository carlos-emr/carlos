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

<%@ page import="java.util.*" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.MyGroup" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.MyGroupPrimaryKey" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.MyGroupDao" %>

<%
    MyGroupDao myGroupDao = SpringUtils.getBean(MyGroupDao.class);

    String curProvider_no = (String) session.getAttribute("user");
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");

    boolean isSiteAccessPrivacy = false;
%>

<security:oscarSec objectName="_site_access_privacy" roleName="<%=roleName$%>" rights="r" reverse="false">
    <%isSiteAccessPrivacy = true; %>
</security:oscarSec>

<%@ page import="java.util.*,java.sql.*" errorPage="/errorpage.jsp" %>

<!DOCTYPE html>
<html>
    <head>

        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admindisplaymygroup.title"/></title>

        <script>
            <!--
            start
            javascript--
            --check
            to
            see
            if it is
            really
            empty in database

            //function upCaseCtrl(ctrl) {
            //	ctrl.value = ctrl.value.toUpperCase();
            //}

            // stop javascript -->
        </script>

        <link href="<%=request.getContextPath() %>/css/bootstrap.min.css" rel="stylesheet">
    </head>


    <body>
    <FORM NAME="UPDATEPRE" id="groupForm" METHOD="post" ACTION="adminnewgroup.jsp">

        <h3><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.btnSearchGroupNoRecords"/></h3>


        <table class="table table-condensed table-hover">
            <thead>
            <tr class="btn-inverse">
                <th></th>
                <th>
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.adminmygroup.formGroupNo"/>
                </th>
                <th>
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admindisplaymygroup.formProviderName"/>
                </th>
            </tr>
            </thead>

            <tbody>
            <%

                String oldNumber = "";
                boolean toggleLine = false;

                List<MyGroup> groupList = myGroupDao.findAll();
                Collections.sort(groupList, MyGroup.MyGroupNoComparator);

                if (isSiteAccessPrivacy) {
                    groupList = myGroupDao.getProviderGroups(curProvider_no);
                }


                for (MyGroup myGroup : groupList) {

                    if (!myGroup.getId().getMyGroupNo().equals(oldNumber)) {
                        toggleLine = !toggleLine;
                        oldNumber = myGroup.getId().getMyGroupNo();
                    }
            %>
            <tr class="<%=toggleLine?"":"info"%>">
                <td width="20px">
                    <input type="checkbox"
                           name="<%=myGroup.getId().getMyGroupNo() + myGroup.getId().getProviderNo()%>"
                           value="<%=myGroup.getId().getMyGroupNo()%>">
                </td>
                <td><%=myGroup.getId().getMyGroupNo()%>
                </td>
                <td><%=myGroup.getLastName() + "," + myGroup.getFirstName()%>
                </td>
            </tr>
            <%
                }
            %>
            </tbody>
        </table>


        <INPUT TYPE="submit" name="submit" class="btn btn-danger"
               VALUE="<fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admindisplaymygroup.btnSubmit1"/>"
               SIZE="7">

        <a href="adminnewgroup.jsp" class="btn btn-primary"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admindisplaymygroup.btnSubmit2"/></a>

    </FORM>

    <script type="text/javascript" src="<%=request.getContextPath() %>/js/jquery-1.9.1.min.js"></script>

    <script>
        function anyChecks() {
            if ($("#groupForm input:checkbox:checked").length > 0) {
                // any one is checked
                return true;
            } else {
                // none is checked
                alert('please select providers(s)');
                return false;
            }
        }

        $('#groupForm').submit(anyChecks);


        $(document).ready(function () {
            parent.parent.resizeIframe($('html').height());

        });

    </script>
    </body>
</html>
