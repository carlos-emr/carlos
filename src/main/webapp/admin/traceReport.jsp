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

<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>

<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>

<security:oscarSec roleName="<%=roleName$%>"
                   objectName="_admin, _admin.traceability" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_admin&type=_admin.traceability");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<html>
    <head>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.traceabilityReport"/></title>
        <!--<script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>-->

        <script src="${ pageContext.servletContext.contextPath }/library/jquery/jquery-3.6.4.min.js"></script>

        <script src="${ pageContext.servletContext.contextPath }/js/bootstrap.min.js"></script>
        <link href="${ pageContext.servletContext.contextPath }/css/bootstrap.min.css" rel="stylesheet">

        <link href="${ pageContext.servletContext.contextPath }/library/jquery/jquery-ui.structure-1.12.1.min.css"
              rel="stylesheet">
        <link href="${ pageContext.servletContext.contextPath }/library/jquery/jquery-ui.theme-1.12.1.min.css"
              rel="stylesheet">
        <script src="${ pageContext.servletContext.contextPath }/library/jquery/jquery-ui-1.12.1.min.js"></script>

        <script language="JavaScript">
            function setfocus() {
                this.focus();
            }

            function generate() {
                document.forms[0].method.value = 'generate';
                document.forms[0].submit();
            }

            function validateInput() {
                if (document.forms[1].file.value == "") {
                    alert("<fmt:setBundle basename='oscarResources'/><fmt:message key='admin.admin.downloadEmpty'/>");
                    return false;
                }
            }

        </script>


        <script>
            $(function () {
                $(document).tooltip();
            });
        </script>

    </head>
    <body>
    <h3><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.traceabilityReport"/></h3>

    <div class="well">
        <h4><fmt:setBundle basename='oscarResources'/><fmt:message key='admin.admin.downloadTraceabilityData'/></h4>
        This screen will allow you to check your OSCAR against another OSCAR to see if it is running the same version.
        You can click "Download Traceability Data from this Oscar" to generate a file with information about the system.
        This file contains information only about the OSCAR program itself -- it does not include any PHI.

        <form action="GenerateTraceAction.do" method="post">
            <input type="hidden" name="method">
            <input type="submit" class="btn btn-primary" value="Download"/>
        </form>
    </div>

    <div class="well">
        <h4><fmt:setBundle basename='oscarResources'/><fmt:message key='admin.admin.traceabilityReport'/></h4>
        If you have a Traceability Data file from another Oscar, you can choose it and click "Generate Traceability
        Report" to create a file which will let you know what files in the OSCAR program have been modified, added, or
        removed.

        <form action="GenerateTraceabilityReportAction.do" method="post" enctype="multipart/form-data"
              onsubmit="return validateInput()">
            <input type="file" name="file"/>
            <span title="<fmt:setBundle basename="oscarResources"/><fmt:message key="global.uploadWarningBody"/>"
                  style="vertical-align:middle;font-family:arial;font-size:20px;font-weight:bold;color:#ABABAB;cursor:pointer"><img
                    alt="alert" src="<%= request.getContextPath() %>/images/icon_alertsml.gif"/></span>

            <br>
            <input class="btn btn-primary" type="submit" name="submit" value="Generate"/>
        </form>

    </div>

    <table>
        <tr>
            <td>
                    ${exception}
            </td>
        </tr>
    </table>
    </body>
</html>