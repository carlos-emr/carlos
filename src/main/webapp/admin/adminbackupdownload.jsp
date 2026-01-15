<!DOCTYPE html>
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

<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" errorPage="/errorpage.jsp" %>
<%@ page import="
      java.util.*,
      java.io.*,
      java.net.*,
      org.apache.commons.io.FileUtils,
      org.apache.commons.text.StringEscapeUtils,
      ca.openosp.OscarProperties,
      ca.openosp.openo.util.FileSortByDate
" %>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core"   prefix="c"   %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt"    prefix="fmt" %>

<%
    // 1) Null-safe session check
    String userRoleAttr = (String) session.getAttribute("userrole");
    String userNameAttr = (String) session.getAttribute("user");
    if (userRoleAttr == null || userNameAttr == null) {
        // no session info → bounce to login
        response.sendRedirect(request.getContextPath() + "/login.jsp");
        return;
    }
    String roleName$ = userRoleAttr + "," + userNameAttr;
    boolean authed = true;
%>
<security:oscarSec
    roleName="<%= roleName$ %>"
    objectName="_admin,_admin.backup"
    rights="r"
    reverse="true">
  <%
    authed = false;
    response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_admin&type=_admin.backup");
  %>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%
    // Load backup_path
    Properties oscarVars = OscarProperties.getInstance();
    String backuppath = oscarVars.getProperty("backup_path");
    session.setAttribute("backupfilepath", backuppath);

    File dir = new File(backuppath);
    boolean exists = dir.exists();
%>

<c:set var="calendarLangKey">
  <fmt:message key="global.javascript.calendar"/>
</c:set>
<c:url value="/share/calendar/lang/${calendarLangKey}" var="calendarLangUrl"/>

<html>
  <head>
    <title>Admin Backup Download</title>
    <link href="<%= request.getContextPath() %>/css/bootstrap.min.css" rel="stylesheet"/>
    <link href="<%= request.getContextPath() %>/css/font-awesome.min.css" rel="stylesheet"/>
    <script type="text/javascript"
            src="${pageContext.request.contextPath}${calendarLangUrl}">
    </script>
  </head>
  <body>
    <h3>
      <fmt:setBundle basename="oscarResources"/>
      <fmt:message key="admin.admin.btnAdminBackupDownload"/>
    </h3>

    <% if (exists) { %>
      <div class="well">
        <table class="table table-striped table-condensed">
          <thead>
            <tr><th>File Name</th><th>Size</th></tr>
          </thead>
          <tbody>
          <%
              // missing or empty path → forward to error
              if (backuppath == null || backuppath.isEmpty()) {
                  request.setAttribute("errorMessage",
                    "backup_path missing in properties; please configure.");
                  request.getRequestDispatcher("/errorpage.jsp")
                         .forward(request, response);
                  return;
              }

              File[] contents = dir.listFiles();
              if (contents == null || contents.length == 0) {
                  throw new Exception(
                    "No files found in " + backuppath +
                    ". Please correct backup_path."
                  );
              }
              Arrays.sort(contents, new FileSortByDate());

              for (File f : contents) {
                  String name = f.getName();
                  if (f.isDirectory() ||
                      name.equals("BackupClient.class") ||
                      name.startsWith(".")) {
                      continue;
                  }

                  // 2) XSS-safe name
                  String safeName = StringEscapeUtils.escapeHtml4(name);

                  String encoded = URLEncoder.encode(name, "UTF-8");
                  long bytes = f.length();
                  String displaySize = FileUtils.byteCountToDisplaySize(bytes);

                  out.println(
                    "<tr>" +
                      "<td><a href=\"" + request.getContextPath() +
                        "/servlet/BackupDownload?filename=" + encoded + "\">" +
                        safeName +
                      "</a></td>" +
                      "<td align=\"right\" title=\"" + bytes + " bytes\">" +
                        displaySize +
                      "</td>" +
                    "</tr>"
                  );
              }
          %>
          </tbody>
        </table>
      </div>
    <% } else { %>
      <div class="alert alert-error">
        <strong>Warning!</strong>
        Backup directory not found—check <i>backup_path</i> in your properties.
      </div>
    <% } %>
  </body>
</html>
