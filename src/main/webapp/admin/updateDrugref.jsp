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
<fmt:setBundle basename="oscarResources"/>

<!DOCTYPE html>
<html>
    <head>
        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">
        <title><fmt:message key="admin.admin.UpdateDrugref"/></title>
        <link href="<c:out value="${ctx}/css/bootstrap.css"/>" rel="stylesheet" type="text/css">

        <script>
            function getUpdateTime() {
                const url = "<c:out value='${ctx}'/>" + "/oscarRx/updateDrugrefDB.do";
                const formData = new URLSearchParams();
                formData.append('method', 'verify');

                fetch(url, {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/x-www-form-urlencoded'
                    },
                    body: formData.toString()
                })
                .then(function(response) {
                    if (!response.ok) {
                        throw new Error('Network response was not ok');
                    }
                    return response.json();
                })
                .then(function(json) {
                    if (json.lastUpdate == null) {
                        document.getElementById('dbInfo').innerHTML = 'Drugref database has not been updated, please update.';
                        document.getElementById('updatedb').style.display = 'block';
                        document.getElementById('statusDisplay').style.display = 'none';
                    } else if (json.lastUpdate === 'updating') {
                        document.getElementById('dbInfo').innerHTML = 'Drugref database is updating';
                        document.getElementById('statusDisplay').style.display = 'none';
                        document.getElementById('updateButton').style.display = 'none';
                    } else {
                        document.getElementById('dbDateTime').innerHTML = json.lastUpdate;
                        document.getElementById('drugDatabaseVersion').innerHTML = json.version;
                        document.getElementById('drugDatabase').innerHTML = json.drugDatabase;
                        document.getElementById('dbInfo').style.display = 'none';
                        document.getElementById('statusDisplay').style.display = 'block';
                    }
                })
                .catch(function(error) {
                    document.getElementById('dbInfo').innerHTML = 'Drugref database has not been updated, please update.';
                    document.getElementById('updatedb').style.display = 'block';
                    document.getElementById('statusDisplay').style.display = 'none';
                });
            }

            function updateDB() {
                const url = "<c:out value='${ctx}'/>" + "/oscarRx/updateDrugrefDB.do";
                const formData = new URLSearchParams();
                formData.append('method', 'updateDB');

                fetch(url, {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/x-www-form-urlencoded'
                    },
                    body: formData.toString()
                })
                .then(function(response) {
                    if (!response.ok) {
                        throw new Error('Network response was not ok');
                    }
                    return response.json();
                })
                .then(function(json) {
                    if (json.result === 'running') {
                        document.getElementById('updateResult').innerHTML = "Update has started, it'll take about 1 hour to finish";
                    } else if (json.result === 'updating') {
                        document.getElementById('updateResult').innerHTML = "Some one has already been updating it";
                    }
                })
                .catch(function(error) {
                    console.error('Error updating database:', error);
                });
            }

            document.addEventListener("DOMContentLoaded", getUpdateTime);
        </script>
      <style>
        #updateButton {
          padding-top: 19px;
        }
        #statusDisplay label {
          font-weight: bold;
          display: inline-block !important;
        }
      </style>
    </head>
    <body class="mainbody">
    <h3><fmt:message key="admin.admin.UpdateDrugref"/></h3>
    <div class="well">
        <div id="dbInfo"></div>
        <div id="statusDisplay" style="display:none;">
          <div>
            <label for="drugDatabase"><fmt:message key="admin.admin.DrugRef.database"/>&colon; </label>
            <span id="drugDatabase"></span></div>
          <div>
            <label for="drugDatabaseVersion"><fmt:message key="admin.admin.DrugRef.databaseVersion"/>&colon; </label>
            <span id="drugDatabaseVersion"></span></div>
          <div>
            <label for="dbDateTime"><fmt:message key="admin.admin.DrugRef.updateDate"/>&colon; </label>
            <span id="dbDateTime"></span></div>
        </div>
        <div id="updateButton">
          <a id="updatedb" onclick="updateDB();" href="javascript:void(0);"
              class="btn btn-primary"><fmt:message key="admin.admin.UpdateDrugref"/></a>
        </div>
        <div><a id="updateResult"></a></div>
    </div>
    </body>

</html>