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
    

    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

--%>

<!DOCTYPE html>
<%@ page errorPage="/errorpage.jsp" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="jakarta.tags.functions" prefix="fn" %>
<%@ taglib uri="http://www.oscar-emr.com/tags/integration" prefix="i" %>

<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>

<%@ page import="java.math.BigInteger,java.util.*,io.github.carlos_emr.carlos.integration.mcedt.mailbox.DetailDataCustom" %>

<%
    BigInteger resourceIDBig = (BigInteger) session.getAttribute("resourceID");
    String resourceID = resourceIDBig.toString();
    List<DetailDataCustom> resourceList = (ArrayList<DetailDataCustom>) session.getAttribute("resourceList");
%>


<html>
    <head>
        <jsp:include page="/mcedt/mailbox/head-includes.jsp"/>
        <link href="mailbox/css/mcedt.css" rel="stylesheet" type="text/css">

        <style type="text/css">
            /* limits table height */
            .scrollable {
                /*
                display: block;
                height: 500px !important;
                max-height: 500px !important;
                overflow: auto;
                */
            }

            /* needed to align elements in navbar */
            select, button {
                margin: 0px !important;
            }

            .navbar {
                line-height: 50px;
                height: 50px;
                vertical-align: middle;
                margin-bottom: 50px;
            }
        </style>
        <script language="javascript">

            window.onload = function () {

                return download();
            }

            function download() {

                var resourceID = '<%= session.getAttribute("resourceID") %>';
                //alert(resourceID);

                if (resourceID != 0) {
                    document.getElementById("resourceId").value = resourceID;

                    var method = jQuery("#method");
                    method.val('download');

                    var form = jQuery("#form");
                    form.submit();
                    return true;
                }

            }

            function gobacktoedthome(control) {
                return submitForm('cancel', control);
            }

            function downloadSelected(control) {
                return submitForm('download', control);
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


        <title>Downloading Claims from MCEDT to CARLOS</title>

        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">

    </head>

    <body>
    <div class="greyBox">
        <div class="center">
            <h1>MCEDT Documents to Download</h1>

            <div>
                <form action="${pageContext.request.contextPath}/mcedt/kaiautodl.do" method="post" id="form">

                    <input id="method" name="method" type="hidden" value=""/>

                    <input type="hidden" id="resourceId" name="resourceId" value=''/>

                    <div style="visibility:hidden">
                        <button id="downloadBut" class="noBorder greenBox flatLink font12"
                                onclick="return downloadSelected();">Download
                        </button>
                    </div>

                </form>
                <%
                    if (resourceList != null) {
                %>
                <table class="table scrollable whiteBox" width="100%" border="0" cellspacing="0" cellpadding="5"
                       style="margin:5px 0 15px;">
                    <thead>
                    <tr class="greenBox">
                        <th>ID</th>
                        <th>Date</th>
                        <th>Type</th>
                        <!-- <th>Result</th>
                        <th>Status</th> -->
                        <th>File Name</th>
                        <th>Download Status</th>
                    </tr>
                    </thead>
                    <c:forEach var="r" items="${resourceList}" varStatus="loopStatus">
                        <tr bgcolor="${loopStatus.index % 2 == 0 ? '#FFF' : '#EEE'}">
                            <td><c:out value="${r.resourceID}"/></td>
                            <td>
                                    <%-- <fmt:formatDate value="${i:toDate(r.createTimestamp)}"/> --%>
                                <fmt:formatDate value="${i:toDate(r.createTimestamp)}" pattern="MM/dd/yyyy hh:mm"/>
                            </td>
                            <td><c:out value="${r.resourceType}"/></td>
                                <%-- <td><c:out value="${r.result.code}" /> - <c:out	value="${r.result.msg}" /></td>
                                <td><c:out value="${r.status}" /></td> --%>
                            <td><c:out value="${r.description}"/></td>
                            <td><c:out value="${r.downloadStatus}"/></td>
                        </tr>
                    </c:forEach>
                </table>
                <%
                } else {
                %>
                <h3>No new documents to download.</h3>
                <%
                    }
                %>
            </div>

            <div>
                <% if (resourceID == "0") { %>
                <button id="backedthome" class="noBorder blackBox flatLink font12 rightMargin5"
                        onclick="return gobacktoedthome();">Return
                </button>
                    <%-- <button class="noBorder blackBox flatLink font12" onclick="window.close()">Close</button> --%>
                <%} %>
                <% if (resourceList != null && resourceID != "0") { %>
                <!-- <button class="btn" onclick="return gobacktoedthome(this)">Cancel</button> -->
                <!-- <button class="btn" onclick="window.close()">Cancel</button> -->
                <%} %>

            </div>
        </div>
    </div>
    </body>
</html>