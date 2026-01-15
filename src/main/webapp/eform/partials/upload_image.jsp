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
<%@ page import="ca.openosp.openo.eform.data.*, ca.openosp.OscarProperties, ca.openosp.openo.eform.*, java.util.*" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>


<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="s" uri="/struts-tags" %>

<html>

    <head>
        <script src="${pageContext.request.contextPath}/js/global.js"></script>
        <script src="<%=request.getContextPath() %>/library/jquery/jquery-3.6.4.min.js"></script>
        <script src="<%=request.getContextPath() %>/library/jquery/jquery-ui-1.12.1.min.js"></script>
        <link href="${pageContext.request.contextPath}/css/bootstrap.css" rel="stylesheet">
        <link href="<%=request.getContextPath() %>/library/jquery/jquery-ui.theme-1.12.1.min.css" rel="stylesheet">
        <link href="<%=request.getContextPath() %>/library/jquery/jquery-ui.structure-1.12.1.min.css" rel="stylesheet">

        <script>
            $(function () {
                $(document).tooltip();
            });
        </script>
    </head>

    <style>
        .message div {
            font-size: 12px;
            display: inline;
        }

        h3 {
            margin: 0px;
            display: inline;
            font-weight: normal;
            font-size: 12px;
        }

        .message ul {
            margin: 0px;
            padding: 0px;
            display: inline;
        }

        .message li {
            list-style: none;
            margin: 0px;
            display: inline;
            padding-left: 6px;
        }

    </style>
    <body>

    <c:if test="${ not empty status }">
        <div class="alert alert-success">
            <button type="button" class="close" data-dismiss="alert">&times;</button>
            <strong>Success!</strong> Your image was uploaded.
            <c:if test="${ not empty sanitizedFileName }">
                <br/>Saved as: <strong>${Encode.formHtmlContent(sanitizedFileName)}</strong>
            </c:if>
        </div>
        <script>
            <c:choose>
                <c:when test="${ not empty sanitizedFileName }">
                    // Delay redirect so user can see the sanitized filename
                    setTimeout(function() {
                        window.top.location.href = "<%=request.getContextPath()%>/administration/?show=ImageUpload";
                    }, 2500);
                </c:when>
                <c:otherwise>
                    window.top.location.href = "<%=request.getContextPath()%>/administration/?show=ImageUpload";
                </c:otherwise>
            </c:choose>
        </script>
    </c:if>

    <div class="row-fluid">
        <div class="well">
            <form action="${pageContext.request.contextPath}/eform/imageUpload.do" enctype="multipart/form-data" method="post">

                <s:if test="hasActionErrors()">
                    <div class="alert alert-danger">
                        <s:actionerror/>
                    </div>
                </s:if>
                <s:if test="hasActionMessages()">
                    <div class="alert alert-info">
                        <s:actionmessage/>
                    </div>
                </s:if>
                <div class="control-group">
                    <div class="controls">
                        <label class="control-label" for="zippedForm"><fmt:setBundle basename="oscarResources"/><fmt:message key="eform.uploadimages.msgFileName"/></label>
                        <input type="file" name="image" id="image" class="check" size="40" required>
                        <span style="color:red;">
		         <i class="icon-warning-sign" title="<fmt:setBundle basename="oscarResources"/><fmt:message key="global.uploadWarningBody"/>"></i>
		         </span>
                        <input type="submit" class="btn btn-primary upload" name="subm"
                               value="<fmt:setBundle basename="oscarResources"/><fmt:message key="eform.uploadimages.btnUpload"/>" disabled>
                    </div>
                </div>
            </form>
        </div>
    </div>


    <script>
        $(document).ready(function () {
            $(".check").on("change", validate).keyup(validate);
        });

        function validate() {

            var id = $(this).attr("id");
            var formHtml = $("#image").val();
            var file = $('#image')[0].files[0];
//var filename = file.name;
//var fileSize = file.size;

//var inputCheck=checkRow(filename);

            if (formHtml != "") {
                $('.upload').removeAttr("disabled");
                $('.upload').addClass("btn-success");
            } else {
                $('.upload').attr("disabled", "disabled");
                $('.upload').removeClass("btn-success");
            }
        }

    </script>
    </body>
</html>