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


<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>


<c:set var="ctx" value="${pageContext.request.contextPath}"/>

<!DOCTYPE html>
<html>
    <head>
        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.UpdateDrugref"/></title>
        <link href="<c:out value="${ctx}/css/bootstrap.css"/>" rel="stylesheet" type="text/css">
        <script src="<c:out value="${ctx}/share/javascript/Oscar.js"/>"></script>
        <script src="<c:out value="${ctx}/share/javascript/prototype.js"/>"></script>
        <script>
            function getUpdateTime() {
                var data = "method=getLastUpdate";
                var url = "<c:out value='${ctx}'/>" + "/oscarRx/updateDrugrefDB.do";
                new Ajax.Request(url, {
                    method: 'post', parameters: data, onSuccess: function (transport) {
                        var json = transport.responseText.evalJSON();
                        if (json.lastUpdate == null) {
                            $('dbInfo').innerHTML = 'Drugref database has not been updated, please update.';
                            $('updatedb').show();
                        } else if (json.lastUpdate == 'updating') {
                            $('dbInfo').innerHTML = 'Drugref database is updating';
                            $('updatedb').hide();
                        } else {
                            $('dbInfo').innerHTML = 'Drugref has been updated on ' + json.lastUpdate;
                            $('updatedb').show();
                        }
                    }, onFailure: function (transport) {
                        $('dbInfo').innerHTML = 'Drugref database has not been updated, please update.';
                        $('updatedb').show();
                    }
                })

            }

            function updateDB() {
                var data = "method=updateDB";
                var url = "<c:out value='${ctx}'/>" + "/oscarRx/updateDrugrefDB.do";
                new Ajax.Request(url, {
                    method: 'post', parameters: data, onSuccess: function (transport) {
                        var json = transport.responseText.evalJSON();
                        if (json.result == 'running')
                            $('updateResult').innerHTML = "Update has started, it'll take about 1 hour to finish";
                        else if (json.result == 'updating')
                            $('updateResult').innerHTML = "Some one has already been updating it";
                    }
                })
            }
        </script>
    </head>
    <body class="mainbody" onload="getUpdateTime();">
    <h4><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.UpdateDrugref"/></h4>
    <div class="well">
        <p><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.DrugRef"/></p>
        <p><a id="dbInfo" href="javascript:void(0);"></a></p>
        <p><a id="updatedb" style="display:none" onclick="updateDB();" href="javascript:void(0);"
              class="btn btn-primary"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.UpdateDrugref"/></a>
        <p><a id="updateResult"></a>
        <p>
    </div>
    </body>

</html>