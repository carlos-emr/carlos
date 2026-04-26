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
<%--
    billingOBECEA.jsp (view) - Ontario EDT OBEC Response Report Generator.
    Rendered by BillingDocumentErrorReportUpload2Action on the "error" result
    so the operator can re-upload an OBEC response file. Auth and any
    project_home / homepath wiring is handled upstream by the action and
    by CarlosProperties; this view is purely presentational.
    @since 2006
--%>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="carlos" prefix="carlos" %>

<!DOCTYPE html>
<html>
    <head>

        <link href="${pageContext.request.contextPath}/library/bootstrap/5.3.8/css/bootstrap.min.css" rel="stylesheet">

        <title>EDT OBEC Response Report Generator</title>
    </head>

    <body>

    <p>EDT OBEC Response Report Generator</p>

    <form action="${pageContext.request.contextPath}/oscarBilling/DocumentErrorReportUpload" method="POST" enctype="multipart/form-data">


        <div class="alert alert-danger">

            <c:if test="${not empty actionErrors}">
                <div class="action-errors">
                    <c:forEach var="error" items="${actionErrors}">
                        <p><carlos:encode value="${error}" context="html"/></p>
                    </c:forEach>
                </div>
            </c:if>
        </div>

        <div class="card card-body bg-body-tertiary">
            Select diskette <input type="file" name="file1" value="" required>

            <input type="submit" name="Submit" class="btn btn-primary" value="Create Report">
        </div>


    </form>
    </body>
</html>
