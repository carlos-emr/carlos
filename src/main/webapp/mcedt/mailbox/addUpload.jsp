<%--

    Copyright (c) 2014-2015. KAI Innovations Inc. All Rights Reserved.
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

<%@ page errorPage="/errorpage.jsp" %>

<!DOCTYPE html>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://www.oscar-emr.com/tags/integration" prefix="i" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>


<html>
    <head>

        <jsp:include page="/mcedt/head-includes.jsp"/>

        <link href="<%= request.getContextPath() %>/mcedt/mailbox/css/mcedt.css" rel="stylesheet" type="text/css">


        <script language="javascript">
            function cancel(control) {
                if (control) {
                    control.disabled = true;
                }
                window.location.href = '<%= request.getContextPath() %>/mcedt/kaimcedt.do?tab=upload';
                return false;
            }

            function create(control) {
                if (control) {
                    control.disabled = true;
                }
                var method = jQuery("#methodAddUpload");
                method.val('addUpload');
                var form = jQuery("#formAddUpload");
                form.submit();
                return true;
            }
        </script>


        <title>MCEDT: Upload</title>

        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">
    </head>

    <body>
    <div class="greyBox">
        <div class="center">
            <h1>Upload Details</h1>

            <form action="${pageContext.request.contextPath}/mcedt/addUpload.do" method="post" id="formAddUpload"
                       enctype="multipart/form-data">

                <jsp:include page="/mcedt/messages.jsp"/>

                <input id="methodAddUpload" name="method" type="hidden" value=""/>
                <label for="addUploadFile">Upload your file</label>
                <input type="file" name="addUploadFile" id="addUploadFile" style="width:95%; margin-top:5px;"/>
                <div class="row topMargin30">
                    <button class="noBorder greenBox flatLink font12 rightMargin5" onclick="return create(this);">
                        Create
                    </button>
                    <button class="noBorder blackBox flatLink font12" onclick="return cancel();">Cancel</button>
                </div>
            </form>
        </div>
    </div>
    </body>
</html>