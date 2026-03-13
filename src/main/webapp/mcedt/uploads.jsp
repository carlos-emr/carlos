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

<%@ page errorPage="/errorpage.jsp" %>

<!DOCTYPE html>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://www.oscar-emr.com/tags/integration" prefix="i" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>


<html>
    <head>
        <jsp:include page="head-includes.jsp"/>
        <script language="javascript">
            function removeSelected(control) {
                return submitForm('removeSelected', control);
            }

            function uploadToMcedt(control) {
                return submitForm('uploadToMcedt', control);
            }

            function cancelUpload(control) {
                return submitForm('cancelUpload', control);
            }

            function addNew(control) {
                return submitForm('addNew', control);
            }

            function submitForm(methodType, control) {
                if (control) {
                    control.disabled = true;
                }

                var method = jQuery("#method");
                method.val(methodType);

                var form = jQuery("#form");
                form.submit();
                return true;
            }
        </script>


        <title>MCEDT: Upload</title>

        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">
    </head>

    <body>
    <div class="container-fluid">
        <div class="row">

            <h2>Create New Upload</h2>

            <form action="${pageContext.request.contextPath}/mcedt/uploads.do" method="post" id="form">

                <jsp:include page="messages.jsp"/>

                <input id="method" name="method" type="hidden" value=""/>

                <c:set var="uploadsEmpty" value="${empty mcedtUploads}"></c:set>
                <c:choose>
                    <c:when test="${uploadsEmpty}">
                        <p>Please add at least one file to the upload.</p>

                        <div>
                            <button class="btn" onclick="return addNew(this)">Add
                                File
                            </button>
                            <button class="btn" onclick="return cancelUpload(this)">Cancel</button>
                        </div>

                    </c:when>
                    <c:otherwise>
                        <table class="table table-striped  table-condensed">
                            <thead>
                            <tr>
                                <th></th>
                                <th>Resource Type</th>
                                <th>Description</th>
                            </tr>
                            </thead>
                            </tr>
                            <c:forEach var="u" items="${mcedtUploads}" varStatus="i">
                                <tr>
                                    <td><input type="checkbox" value="${i.index}"
                                               name="resourceId"/></td>
                                    <td><c:out value="${u.resourceType}"/></td>
                                    <td><c:out value="${u.description}"/></td>
                                </tr>
                            </c:forEach>
                        </table>

                        <div>
                            <button class="btn" onclick="return uploadToMcedt(this)">Upload to MCEDT</button>
                            <button class="btn" onclick="return removeSelected(this)">Remove Selected Files</button>
                            <button class="btn" onclick="return addNew(this)">Add More Files</button>
                            <button class="btn" onclick="return cancelUpload(this)">Cancel</button>
                        </div>
                    </c:otherwise>
                </c:choose>
            </form>
        </div>
    </div>
    </body>
</html>