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
<!DOCTYPE html>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_admin,_admin.userAdmin" rights="*" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_admin&type=_admin.userAdmin");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>


<%@ page errorPage="/errorpage.jsp" %>
<%@ page import="java.util.*" %>
<%@ page import="java.sql.BatchUpdateException" %>
<%@ page import="java.sql.*" %>
<%@ page import="ca.openosp.openo.util.*" %>
<%@ page import="ca.openosp.openo.login.*" %>
<%@ page import="ca.openosp.openo.log.*" %>
<%@ page import="ca.openosp.openo.utility.SpringUtils" %>
<%@ page import="ca.openosp.openo.commn.model.SecRole" %>
<%@ page import="ca.openosp.openo.commn.dao.SecRoleDao" %>
<%@ page import="ca.openosp.openo.commn.model.SecPrivilege" %>
<%@ page import="ca.openosp.openo.commn.dao.SecPrivilegeDao" %>
<%@ page import="ca.openosp.openo.commn.model.SecObjectName" %>
<%@ page import="ca.openosp.openo.commn.dao.SecObjectNameDao" %>
<%@ page import="ca.openosp.openo.commn.dao.SecObjectNameDaoImpl" %>
<%@ page import="ca.openosp.openo.commn.model.ProviderData" %>
<%@ page import="ca.openosp.openo.commn.dao.ProviderDataDao" %>
<%@ page import="ca.openosp.openo.commn.model.SecObjPrivilege" %>
<%@ page import="ca.openosp.openo.commn.model.SecObjPrivilegePrimaryKey" %>
<%@ page import="ca.openosp.openo.commn.dao.SecObjPrivilegeDao" %>
<%@ page import="ca.openosp.openo.commn.model.RecycleBin" %>
<%@ page import="ca.openosp.openo.commn.dao.RecycleBinDao" %>
<%@ page import="org.springframework.dao.DataIntegrityViolationException" %>
<%@ page import="org.springframework.web.util.HtmlUtils" %>
<%@ page import="org.apache.commons.text.StringEscapeUtils" %>
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ page import="ca.openosp.openo.log.LogAction" %>
<%@ page import="ca.openosp.openo.log.LogConst" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%
    SecRoleDao secRoleDao = SpringUtils.getBean(SecRoleDao.class);
    SecPrivilegeDao secPrivilegeDao = SpringUtils.getBean(SecPrivilegeDao.class);
    SecObjectNameDao secObjectNameDao = SpringUtils.getBean(SecObjectNameDaoImpl.class);
    ProviderDataDao providerDataDao = SpringUtils.getBean(ProviderDataDao.class);
    SecObjPrivilegeDao secObjPrivilegeDao = SpringUtils.getBean(SecObjPrivilegeDao.class);
    RecycleBinDao recycleBinDao = SpringUtils.getBean(RecycleBinDao.class);
    String curUser_no = (String) session.getAttribute("user");
    String ip = request.getRemoteAddr();
%>

<%
    String msg = "";
    String sql = null;
    ResultSet rs = null;

// get role from database
    Vector vecRoleName = new Vector();

    List<SecRole> secRoles = secRoleDao.findAllOrderByRole();
    for (SecRole secRole : secRoles) {
        vecRoleName.add(secRole.getName());
    }


// get rights from database
    Vector vecRightsName = new Vector();
    Vector vecRightsDesc = new Vector();
    List<SecPrivilege> secPrivileges = secPrivilegeDao.findAll();
    for (SecPrivilege sp : secPrivileges) {
        vecRightsName.add(sp.getPrivilege());
        vecRightsDesc.add(sp.getDescription());
    }

// get objId from database
    Vector vecObjectId = new Vector();
    List<SecObjectName> objectNames = secObjectNameDao.findAll();
    for (SecObjectName objectName : objectNames) {
        vecObjectId.add(objectName.getId());
    }

    List<String> distinctObjectNames = secObjectNameDao.findDistinctObjectNames();
    for (String objectName : distinctObjectNames) {
        if (!vecObjectId.contains(objectName))
            vecObjectId.add(objectName);
    }

// get providers name from database
    Vector vecProviderName = new Vector();
    Vector vecProviderNo = new Vector();
    List<ProviderData> providers = providerDataDao.findAllOrderByLastName();
    for (ProviderData provider : providers) {
        vecProviderNo.add(provider.getId());
        vecProviderName.add(provider.getLastName() + "," + provider.getFirstName());
    }

// add the role list
    if (request.getParameter("submit") != null && request.getParameter("submit").equals("Add")) {
        String roleUserGroup = request.getParameter("roleUserGroup");
        if (roleUserGroup.equals("")) roleUserGroup = request.getParameter("roleUserGroup1");

        Vector vecObjRowNo = new Vector();
        for (Enumeration e = request.getParameterNames(); e.hasMoreElements(); ) {
            String paraName = (String) e.nextElement();
            if (paraName.startsWith("object$")) {
                vecObjRowNo.add(paraName.substring("object$".length()));
            }
        }

        for (int i = 0; i < vecObjRowNo.size(); i++) {
            String objectName = (String) vecObjRowNo.get(i);
            if (objectName.equals("Name1") && request.getParameter("object$Name1").trim().equals("")) continue;

            String privilege = "";
            for (Enumeration e = request.getParameterNames(); e.hasMoreElements(); ) {
                String paraName = (String) e.nextElement();
                String prefix = "privilege$" + objectName + "$";
                if (paraName.startsWith(prefix)) {
                    privilege += paraName.substring(prefix.length());
                }
            }
            String prefix = "priority$" + objectName;
            String priority = request.getParameter(prefix);
            if (objectName.equals("Name1")) objectName = request.getParameter("object$Name1").trim();

            String encodedRoleUserGroup = Encode.forHtmlContent(StringUtils.trimToEmpty(roleUserGroup));
            String encodedObjectName = Encode.forHtmlContent(StringUtils.trimToEmpty(objectName));
            SecObjPrivilege sop = new SecObjPrivilege();
            sop.setId(new SecObjPrivilegePrimaryKey());
            sop.getId().setRoleUserGroup(roleUserGroup);
            sop.getId().setObjectName(objectName.trim());
            sop.setPrivilege(privilege);
            sop.setPriority(Integer.parseInt(priority));
            sop.setProviderNo(curUser_no);
            String secExceptionMsg = new String();
            try {
                secObjPrivilegeDao.persist(sop);
            } catch (DataIntegrityViolationException divEx) {
                secExceptionMsg = divEx.getMostSpecificCause().getLocalizedMessage();
            }
            if (secExceptionMsg.length() > 0)
                msg += secExceptionMsg;
            else {
                msg += "Role/Obj/Rights " + encodedRoleUserGroup + "/" + encodedObjectName + "/" + privilege + " is added. ";
                LogAction.addLog(curUser_no, LogConst.ADD, LogConst.CON_PRIVILEGE, roleUserGroup + "|" + encodedObjectName + "|" + privilege, ip);
            }
        }
    }

// update the role list
    if (request.getParameter("buttonUpdate") != null && request.getParameter("buttonUpdate").length() > 0) {
        String roleUserGroup = request.getParameter("roleUserGroup");
        String encodedRoleUserGroup = Encode.forHtmlContent(StringUtils.trimToEmpty(roleUserGroup));
        String objectName = request.getParameter("objectName");
        String encodedObjectName = Encode.forHtmlContent(StringUtils.trimToEmpty(objectName));

        String privilege = request.getParameter("privilege");
        String priority = request.getParameter("priority");
        String provider_no = request.getParameter("provider_no");

        SecObjPrivilege sop = secObjPrivilegeDao.find(new SecObjPrivilegePrimaryKey(roleUserGroup, objectName));
        if (sop != null) {
            privilege = sop.getPrivilege();
            priority = String.valueOf(sop.getPriority());
            provider_no = sop.getProviderNo();
        }

        RecycleBin recycleBin = new RecycleBin();
        recycleBin.setProviderNo(curUser_no);
        recycleBin.setUpdateDateTime(new java.util.Date());
        recycleBin.setTableName("secObjPrivilege");
        recycleBin.setKeyword(roleUserGroup + "|" + objectName);

        String xml = "<roleUserGroup>" + roleUserGroup + "</roleUserGroup>" + "<objectName>" + objectName + "</objectName>";
        xml += "<privilege>" + privilege + "</privilege>" + "<priority>" + priority + "</priority>";
        xml += "<provider_no>" + provider_no + "</provider_no>";

        recycleBin.setTableContent(xml);
        recycleBinDao.persist(recycleBin);


        //String privilege = request.getParameter("privilege");
        privilege = "";
        for (Enumeration e = request.getParameterNames(); e.hasMoreElements(); ) {
            String paraName = (String) e.nextElement();
            if (paraName.startsWith("privilege")) {
                privilege += paraName.substring("privilege".length());
            }
        }
        priority = request.getParameter("priority");
        provider_no = curUser_no;

        sop = secObjPrivilegeDao.find(new SecObjPrivilegePrimaryKey(roleUserGroup, objectName));
        if (sop != null) {
            sop.setProviderNo(provider_no);
            sop.setPrivilege(privilege);
            sop.setPriority(Integer.parseInt(priority));
            secObjPrivilegeDao.merge(sop);
            msg = "Role/Obj/Rights " + roleUserGroup + "/" + objectName + "/" + privilege + " is updated. ";
            LogAction.addLog(curUser_no, LogConst.UPDATE, LogConst.CON_PRIVILEGE, roleUserGroup + "|" + objectName + "|" + privilege, ip);
        } else {
            msg = "Role/Obj/Rights " + encodedRoleUserGroup + "/" + encodedObjectName + "/" + privilege + " is <span style='color:red'>NOT</span> updated!!! ";
        }

    }


// delete the role list
    if (request.getParameter("submit") != null && request.getParameter("submit").equals("Delete")) {
        String roleUserGroup = request.getParameter("roleUserGroup");
        String encodedRoleUserGroup = Encode.forHtmlContent(StringUtils.trimToEmpty(roleUserGroup));
        String objectName = request.getParameter("objectName");
        String encodedObjectName = Encode.forHtmlContent(StringUtils.trimToEmpty(objectName));

        String privilege = request.getParameter("privilege");
        String priority = request.getParameter("priority");
        String provider_no = request.getParameter("provider_no");

        SecObjPrivilege sop = secObjPrivilegeDao.find(new SecObjPrivilegePrimaryKey(roleUserGroup, objectName));
        if (sop != null) {
            privilege = sop.getPrivilege();
            priority = String.valueOf(sop.getPriority());
            provider_no = sop.getProviderNo();
            secObjPrivilegeDao.remove(sop.getId());
            msg = "Role/Obj/Rights " + encodedRoleUserGroup + "/" + encodedObjectName + "/" + privilege + " is deleted. ";

            RecycleBin recycleBin = new RecycleBin();
            recycleBin.setProviderNo(curUser_no);
            recycleBin.setUpdateDateTime(new java.util.Date());
            recycleBin.setTableName("secObjPrivilege");
            recycleBin.setKeyword(roleUserGroup + "|" + objectName);
            String xml = "<roleUserGroup>" + roleUserGroup + "</roleUserGroup>" + "<objectName>" + objectName + "</objectName>";
            xml += "<privilege>" + privilege + "</privilege>" + "<priority>" + priority + "</priority>";
            xml += "<provider_no>" + provider_no + "</provider_no>";
            recycleBin.setTableContent(xml);
            recycleBinDao.persist(recycleBin);
            LogAction.addLog(curUser_no, LogConst.DELETE, LogConst.CON_PRIVILEGE, roleUserGroup + "|" + objectName, ip);
        } else {
            msg = "Role/Obj/Rights " + encodedRoleUserGroup + "/" + encodedObjectName + "/" + privilege + " is <span style='color:red'>NOT</span> deleted!!! ";
        }
    }

    String keyword = request.getParameter("keyword") != null ? request.getParameter("keyword") : "";

%>
<html>
<head>
    <title>PROVIDER</title>

    <link href="${pageContext.request.contextPath}/library/DataTables-1.10.12/media/css/jquery.dataTables.min.css"
          rel="stylesheet">
    <link href="${ pageContext.request.contextPath }/css/DT_bootstrap.css" rel="stylesheet" type="text/css">

    <link href="${pageContext.request.contextPath}/css/bootstrap.css" rel="stylesheet" type="text/css">
    <!-- Bootstrap 2.3.1 -->

    <script src="${pageContext.request.contextPath}/library/jquery/jquery-3.6.4.min.js"></script>
    <script src="${pageContext.request.contextPath}js/global.js"></script>


    <script src="${ pageContext.request.contextPath }/library/DataTables/datatables.min.js"></script>
    <!-- DataTables 1.13.4 -->

    <script>
        jQuery(document).ready(function () {
            jQuery('#addtbl').DataTable({
                "lengthMenu": [[8, 16, 32, -1], [8, 16, 32, "<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.LeftNavBar.AllLabs"/>"]],
                "order": [],
                "language": {
                    "url": "<%=request.getContextPath() %>/library/DataTables/i18n/<fmt:setBundle basename="oscarResources"/><fmt:message key="global.i18nLanguagecode"/>.json"
                }
            });
        });
    </script>

    <script language="JavaScript" type="text/javascript">
        <!--
        function setfocus() {
            this.focus();
            document.forms[0].keyword.select();
        }

        function submit(form) {
            form.submit();
        }

        newBrowser = (document.getElementById) ? 1 : 0;

        function onChangeSelect() {
            if (newBrowser) {
                //me.style.backgroundColor='red';
                if (document.myform2.roleUserGroup.selectedIndex == 0) {
                    document.myform2.roleUserGroup1.style.backgroundColor = 'white';
                    document.myform2.roleUserGroup1.style.color = 'black';
                    //document.myform2.roleUserGroup1.style.visibility = 'hidden';
                } else {
                    document.myform2.roleUserGroup1.style.backgroundColor = 'silver';
                    document.myform2.roleUserGroup1.style.color = 'silver';
                }
                if (document.myform2.objectName.selectedIndex == 0) {
                    document.myform2.objectName1.style.backgroundColor = 'white';
                    document.myform2.objectName1.style.color = 'black';
                } else {
                    document.myform2.objectName1.style.backgroundColor = 'silver';
                    document.myform2.objectName1.style.color = 'silver';
                }
            }
        }

        // -->
    </script>

    <script>

        function uncheckSiblings(el) {
            var myName = el.attr('name');
            el.parents("td").find("input:checkbox[name ^= 'privilege']").each(function () {
                if (jQuery(this).attr('name') != myName) {
                    jQuery(this).prop("checked", false);
                }
            });
        }

        function uncheckSiblings1(el) {
            var myName = el.attr('name');
            el.parents("td").find("input:checkbox[name ^= 'privilege']").each(function () {
                if (jQuery(this).attr('name') != myName && jQuery(this).attr('name').endsWith("x")) {
                    jQuery(this).prop("checked", false);
                }
                if (jQuery(this).attr('name') != myName && jQuery(this).attr('name').endsWith("o")) {
                    jQuery(this).prop("checked", false);
                }
            });

        }

        jQuery(document).ready(function () {
            jQuery("input:checkbox[name ^= 'privilege']").on("change", function () {
                if (jQuery(this).is(":checked")) {
                    var priv = jQuery(this).attr('name')[jQuery(this).attr('name').length - 1];
                    if (priv === 'x' || priv === 'o') {
                        uncheckSiblings(jQuery(this));
                    }

                    if (priv === 'r' || priv === 'w' || priv === 'u' || priv === 'd') {
                        uncheckSiblings1(jQuery(this));
                    }

                }

            });
        });

    </script>
</head>
<body>

<form name="myform" action="providerPrivilege.jsp" method="POST">
    <table width="100%">
        <tr>
            <th><% if (msg.length() > 1) {%>
                <div class="alert" style="width:100%; text-align:center"><%=msg%>
                </div>
                <% } %></th>
            <th style="width: 600px">Object Name/Role Name: <input type="text" name="keyword"
                                                                   value="<%=Encode.forHtmlAttribute(keyword)%>"> <input
                    type="submit" name="search" class="btn"
                    value="Filter"></th>
        </tr>
    </table>
</form>

<%
    String color = "#fff";
    Properties prop = null;
    Vector<Properties> vec = new Vector<Properties>();

    List<SecObjPrivilege> sops = null;
    if ("".equals(keyword) || vecRoleName.contains(keyword)) {
        sops = secObjPrivilegeDao.findByRoleUserGroup(keyword + "%");
    } else {
        sops = secObjPrivilegeDao.findByObjectName(keyword + "%");
    }
    for (SecObjPrivilege sop : sops) {
        prop = new Properties();
        prop.setProperty("roleUserGroup", Encode.forHtmlAttribute(sop.getId().getRoleUserGroup()));
        prop.setProperty("objectName", Encode.forHtmlAttribute(sop.getId().getObjectName()));
        prop.setProperty("privilege", sop.getPrivilege());
        prop.setProperty("priority", String.valueOf(sop.getPriority()));
        vec.add(prop);
    }

%>

<h4>Role/Privilege List</h4>
<div class="well">
    <table id="tblpp" class="table table-condensed">
        <thead>
        <tr>
            <th style="width:300px">Role</th>
            <th style="width:200px">Object ID</th>
            <th style="width:300px">Privilege</th>
            <th>Priority</th>
            <th>Action</th>
        </tr>
        </thead>
        <tbody>
        <%
            String tempNo = null;
            String bgColor = color;
            for (int i = 0; i < vec.size(); i++) {
                bgColor = bgColor.equals("#f5f5f5;") ? color : "#f5f5f5;";
                String roleUser = (vec.get(i)).getProperty("roleUserGroup", "");
                String roleUserName = vecProviderNo.contains(roleUser) ? "" + (String) vecProviderName.get(vecProviderNo.indexOf(roleUser)) + "" : roleUser;
                String obj = (vec.get(i)).getProperty("objectName", "");
        %>
        <form name="myformrow<%=i%>" action="providerPrivilege.jsp"
              method="POST">
            <tr style="background-color:<%=bgColor%>">
                <td><%= roleUserName %>
                </td>
                <td><%= obj %>
                </td>
                <td style="text-align:left">
                    <%
                        String priv = (vec.get(i)).getProperty("privilege", "");
                        boolean bSet = true;
                        for (int j = 0; j < vecRightsName.size(); j++) {
                            if (bSet && ((String) vecRightsName.get(j)).startsWith("o")) {
                                out.print("</br>");
                                bSet = false;
                            }
                    %> <input type="checkbox" name="privilege<%=vecRightsName.get(j)%>"
                    <%=priv.indexOf(((String)vecRightsName.get(j)))>=0?"checked":""%> >
                    <%=((String) vecRightsDesc.get(j)).replaceAll("Only", "O")%>
                    <% }%> <!--input type="text" name="privilege" value="<%--= priv--%>" /-->
                </td>
                <td><select name="priority" class="input-min" style="width:50px">
                    <option value="">-</option>
                    <% for (int j = 10; j >= 0; j--) {%>
                    <option value="<%=j%>"
                            <%= ("" + j).equals((vec.get(i)).getProperty("priority", "")) ? "selected" : "" %>><%= j %>
                    </option>
                    <% } %>
                </select></td>
                <td style="text-align:center">
                    <% if (!roleUser.equals("admin") && !obj.equals("_admin")) { %> <input
                        type="hidden" name="keyword" value="<%=Encode.forHtmlAttribute(keyword)%>"> <input
                        type="hidden" name="objectName" value="<%=obj %>"> <input
                        type="hidden" name="roleUserGroup" value="<%=roleUser %>"> <input
                        type="submit" name="buttonUpdate" value="Update" class="btn"> <input
                        type="submit" name="submit" value="Delete" class="btn"> <% } %>
                </td>
            </tr>
        </form>
        <% } %>
        </tbody>
    </table>
</div>


<h4>Add Role/Privilege</h4>
<div class="well">
    <form name="myform2" action="providerPrivilege.jsp" method="POST">
        For:
        <select name="roleUserGroup"
                onChange="onChangeSelect()">
            <option value="">-</option>
            <% for (int j = 0; j < vecRoleName.size(); j++) {%>
            <option value="<%=Encode.forHtmlAttribute(vecRoleName.get(j).toString())%>"><%= Encode.forHtmlContent(vecRoleName.get(j).toString()) %>
            </option>
            <% }%>
        </select> or <select name="roleUserGroup1">
        <option value="">-</option>
        <% for (int j = 0; j < vecProviderNo.size(); j++) {%>
        <option value="<%=vecProviderNo.get(j)%>"><%= Encode.forHtmlContent((String) vecProviderName.get(j)) %>
        </option>
        <% }%>
        <option value="_principal">_principal</option>
    </select>

        <table id="addtbl" style="width: 100%" class="table table-striped table-condensed">
            <thead>
            <tr>
                <th style="width:300px">Role</th>
                <th style="width:200px">Object ID</th>
                <th style="width:300px">Privilege</th>
                <th>Priority</th>
                <th>Action</th>
            </tr>
            </thead>
            <tbody>

            <% for (int i = 0; i < vecObjectId.size(); i++) {
                if (i != vecObjectId.size() && ((String) vecObjectId.get(i)).indexOf("$") >= 0) {
                    continue;
                }
            %>

            <tr>
                <td>

                </td>
                <td>
                    <%
                        String objName = "";
                        if (i == vecObjectId.size()) {
                            objName = "Name1";
                    %> <input type="text" name="object$<%=objName%>" value=""> <% } else {

                    objName = (String) vecObjectId.get(i);
                %> <input type="checkbox" name="object$<%=objName%>"> <%= vecObjectId.get(i) %>
                    <% if (objName.startsWith("_queue.")) {
                        String d = null;
                        SecObjectName son = secObjectNameDao.find(objName);
                        if (son != null) {
                            d = son.getDescription();
                        }

                        if (d == null || d.equalsIgnoreCase("null") || d.trim().length() == 0) {
                            d = "";
                        } else {
                            d = "(" + d + ")";
                        }
                    %>

                    <%=d%>
                    <%
                            }
                        }
                    %>
                </td>
                <td>
                    <%
                        boolean bSet = true;
                        for (int j = 0; j < vecRightsName.size(); j++) {
                            if (bSet && ((String) vecRightsName.get(j)).startsWith("o")) {
                                out.print("</br>");
                                bSet = false;
                            }
                    %> <input type="checkbox"
                              name="privilege$<%=objName%>$<%=vecRightsName.get(j)%>"/> <%=vecRightsDesc.get(j)%>
                    <% }%>
                </td>
                <td><select name="priority$<%=objName%>" style="width:50px;">
                    <option value="">-</option>
                    <% for (int j = 10; j >= 0; j--) { %>
                    <option value="<%=j%>" <%= ("" + j).equals("0") ? "selected" : "" %>>
                        <%= j %>
                    </option>
                    <% }%>
                </select></td>
                <td>
                    <input type="submit"
                           name="submit" value="Add" class="btn"></td>
            </tr>
            <% }%>


            </tbody>
        </table>
        <br>
        <table style="width:100%" class="table table-condensed">
            <tbody>
            <tr>
                <td style="width:300px">

                </td>
                <td style="width:200px">
                    <input type="text" name="object$Name1" value="" placeholder="new security object">
                </td>
                <td style="width:300px">
                    <%
                        boolean bSet = true;
                        for (int j = 0; j < vecRightsName.size(); j++) {
                            if (bSet && ((String) vecRightsName.get(j)).startsWith("o")) {
                                out.print("</br>");
                                bSet = false;
                            }
                    %> <input type="checkbox"
                              name="privilege$Name1$<%=vecRightsName.get(j)%>"> <%=vecRightsDesc.get(j)%>
                    <% }%>
                </td>
                <td>Priority <select name="priority$Name1" style="width:50px;">
                    <option value="">-</option>
                    <% for (int j = 10; j >= 0; j--) { %>
                    <option value="<%=j%>" <%= ("" + j).equals("0") ? "selected" : "" %>>
                        <%= j %>
                    </option>
                    <% }%>
                </select></td>
                <td><input type="submit"
                           name="submit" value="Add" class="btn"></td>

            </tr>
            </tbody>
        </table>
        <input type="hidden"
               name="keyword" value="<%=Encode.forHtmlAttribute(keyword)%>">

    </form>
</div>

</body>
</html>