<!DOCTYPE html>
<%--

    Copyright (c) 2006-. OSCARservice, OpenSoft System. All Rights Reserved.
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


    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

--%>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>

<html>
    <head>
        <title><fmt:message key="admin.admin.manageGSTControl"/></title>
        <link href="${pageContext.request.contextPath}/library/bootstrap/5.3.8/css/bootstrap.min.css" rel="stylesheet">
        <script type="text/javascript">
            function submitcheck() {
                document.getElementById("gstPercent").value = extractNums(document.getElementById("gstPercent").value);
            }

            function extractNums(str) {
                return str.replace(/\D/g, "");
            }
        </script>
    </head>
    <body>

    <h3><fmt:message key="admin.admin.manageGSTControl"/></h3>

    <form action="${pageContext.request.contextPath}/admin/GstControl" method="post">
        GST:<br>
        <div class="input-group">
            <input type="text" class="form-control" maxlength="3" id="gstPercent" name="gstPercent"
                   value="<carlos:encode value='${gstControlModel.gstPercent}' context='htmlAttribute'/>"/>
            <span class="input-group-text">%</span>
        </div>
        <br>
        <input class="btn btn-primary" type="submit" value="save" onclick="submitcheck()"/>
    </form>
    </body>
</html>
