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

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ page errorPage="/WEB-INF/jsp/error/errorpage.jsp" %>
<%@ page import="java.util.*" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Security" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.SecurityDao" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%
    SecurityDao securityDao = SpringUtils.getBean(SecurityDao.class);
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    String curUser_no = (String) session.getAttribute("user");

    boolean isSiteAccessPrivacy = false;
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_admin,_admin.userAdmin,_admin.unlockAccount" rights="r"
                   reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/error/SecurityError.do?type=_admin&type=_admin.userAdmin&type=_admin.unlockAccount");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>


<security:oscarSec objectName="_site_access_privacy" roleName="<%=roleName$%>" rights="r"
                   reverse="false"><%isSiteAccessPrivacy = true; %>
</security:oscarSec>


<%
    // Read unlock result message and lock list from the Action (UnLock2Action)
    String msg = (String) request.getAttribute("msg");
    if (msg == null) msg = "";

    @SuppressWarnings("unchecked")
    Vector vec = (Vector) request.getAttribute("lockList");
    if (vec == null) vec = new Vector();

    //multi-office limit
    if (isSiteAccessPrivacy && vec.size() > 0) {

        List<String> userList = new ArrayList<String>();
        List<Security> securityList = securityDao.findByProviderSite(curUser_no);

        for (Security security : securityList) {
            userList.add(security.getUserName());
        }

        for (int i = 0; i < vec.size(); i++) {
            if (!userList.contains((String) vec.get(i))) {
                vec.remove((String) vec.get(i));
            }
        }
    }

%>

<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Strict//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd">
<html>
    <head>
        <link href="<%=request.getContextPath() %>/library/bootstrap/5.3.8/css/bootstrap.min.css" rel="stylesheet" type="text/css">
        <link rel="stylesheet" href="<%=request.getContextPath() %>/css/fontawesome-all.min.css">
        <title><fmt:message key="admin.admin.unlockAcct"/></title>
        <script type="text/javascript" language="JavaScript">

            <!--

            function onSearch() {
            }

            //-->

        </script>
    </head>
    <body>
    <div width="100%">
        <div id="header"><H4><i class="fa-solid fa-unlock"></i>&nbsp;<fmt:message key="admin.admin.unlockAcct"/></H4>
        </div>
    </div>

    <form method="post" name="baseurl" action="${pageContext.request.contextPath}/admin/UnLock.do">
        <% if (!msg.isEmpty()) { %>
        <div class="alert alert-success">
            <%= Encode.forHtml(msg) %>
        </div>
        <% } %>
        <div class="card card-body bg-body-tertiary">
            <b><fmt:message key="admin.providersearchresults.ID"/></b>
            <select name="userName">
                <% for (int i = 0; i < vec.size(); i++) { %>
                <option value="<%=Encode.forHtmlAttribute((String) vec.get(i))%>"><%=Encode.forHtmlContent((String) vec.get(i))%>
                </option>
                <% } %>
            </select> <input type="submit" name="submit" class="btn btn-primary"
                             value="<fmt:message key="admin.admin.unlockAcct"/>"/>
        </div>


    </form>

    </body>
</html>