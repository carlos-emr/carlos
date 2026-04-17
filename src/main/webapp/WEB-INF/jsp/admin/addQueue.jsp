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

<!DOCTYPE HTML>

<%@page import="io.github.carlos_emr.carlos.providers.data.*,java.util.*,io.github.carlos_emr.carlos.utility.SpringUtils,io.github.carlos_emr.carlos.commn.dao.QueueDao" %>
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<fmt:setBundle basename="oscarResources"/>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_admin" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_admin");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>

<html lang="en">
<head>
    <title><fmt:message key="admin.addQueue.title"/></title>

    <style type="text/css">
        .input-queue {
            font-size: 18px !important;
        }

        .alert {
            display: none;
        }

        .alert em {
            font-size: 18px;
        }
    </style>
    <link rel="stylesheet" href="<%=request.getContextPath() %>/css/fontawesome-all.min.css">
</head>

<body>

<h3><fmt:message key="admin.addQueue.heading"/></h3>

<div class="card card-body bg-body-tertiary">


    <form class="d-flex flex-wrap align-items-center gap-2" id="addQueueForm">
        <input type="text" id="newQueueName" class="form-control input-queue" placeholder="<fmt:message key='admin.addQueue.placeholder'/>" value=""/>
        <input type="button" class="btn btn-primary" value="<fmt:message key='admin.addQueue.btnAdd'/>" id="add-btn"/>

        <i class="fa-solid fa-circle-question" style="margin-left:20px;"></i>
    </form>

</div>

<div class="alert">
    <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>
    <div id="addQueueSuccessMsg">
        <strong><fmt:message key="admin.addQueue.msgWarning"/></strong> <fmt:message key="admin.addQueue.msgWarningBox"/>
    </div>
</div>


<h4><fmt:message key="admin.addQueue.existingQueues"/></h4>

					   <ol>
                       <%
                        QueueDao queueDao = (QueueDao) SpringUtils.getBean(QueueDao.class);
                        List<Hashtable> queues=queueDao.getQueues();
                        for(Hashtable qht:queues){
                        %>                            
                                <li><e:forHtmlContent value='<%= (String) qht.get("queue") %>' /></li>
                        <%}%>
                        </ol>
 
</body>

<script type="text/javascript">

    var pageTitle = document.title;
    document.title = '<fmt:message key="admin.addQueue.documentTitle"/>';

    $(document).ready(function ($) {

        $("#add-btn").click(function (e) {
            e.preventDefault();

            var qn = $('#newQueueName').val();
            qn = qn.replace(/^\s+/g, "");
            qn = qn.replace(/\s+$/g, "");

            if (qn == "default") {
                $('.alert').removeClass('alert-success');
                $('.alert').addClass('alert-danger');
                $('.alert').show();

                $('#addQueueSuccessMsg').html("<strong><fmt:message key='admin.addQueue.msgError'/></strong> <fmt:message key='admin.addQueue.msgDefaultQueueError'/>");
            } else {

                if (qn.length > 0) {
                    var data = "method=addNewQueue&newQueueName=" + qn;
                    var url = "${ctx}/documentManager/inboxManage";

                    $.ajax({
                        url: url,
                        method: 'POST',
                        data: data,
                        dataType: "json",
                        success: function (data) {
                            $('.alert').addClass('alert-success');
                            $('.alert').show();

                            $('#addQueueSuccessMsg').html("<strong><fmt:message key='admin.addQueue.msgSuccess'/></strong> <fmt:message key='admin.addQueue.msgQueueAddedPrefix'/> <em>" + $('<div/>').text(qn).html() + "</em> <fmt:message key='admin.addQueue.msgQueueAddedSuffix'/>");
                            $('#newQueueName').val("");

                            var json = data.addNewQueue;
                            if (json != null) {
                                if (json == true) {
                                    $('.alert').removeClass('alert-danger');
                                    $('.alert').addClass('alert-success');
                                    $('.alert').show();

                                    $('#addQueueSuccessMsg').html("<strong><fmt:message key='admin.addQueue.msgSuccess'/></strong> <fmt:message key='admin.addQueue.msgQueueAddedPrefix'/> <em>" + $('<div/>').text(qn).html() + "</em> <fmt:message key='admin.addQueue.msgQueueAddedSuffix'/>");
                                    $('#newQueueName').val("");
                                } else {
                                    $('.alert').removeClass('alert-success');
                                    $('.alert').addClass('alert-danger');
                                    $('.alert').show();

                                    $('#addQueueSuccessMsg').html("<strong><fmt:message key='admin.addQueue.msgError'/></strong> <fmt:message key='admin.addQueue.msgQueueNotAddedPrefix'/> <em>" + $('<div/>').text(qn).html() + "</em> <fmt:message key='admin.addQueue.msgQueueNotAddedSuffix'/>");
                                }
                            }

                        },
                        error: function (data) {
                            $('.alert').removeClass('alert-success');
                            $('.alert').addClass('alert-danger');
                            $('.alert').show();

                            $('#addQueueSuccessMsg').html("<strong><fmt:message key='admin.addQueue.msgError'/></strong> <fmt:message key='admin.addQueue.msgQueueContactPrefix'/> <em>" + $('<div/>').text(qn).html() + "</em> <fmt:message key='admin.addQueue.msgQueueContactSuffix'/>");
                        }


                    });


                } else {
                    $('.alert').removeClass('alert-success');
                    $('.alert').addClass('alert-danger');
                    $('.alert').show();

                    $('#addQueueSuccessMsg').html("<strong><fmt:message key='admin.addQueue.msgError'/></strong> <fmt:message key='admin.addQueue.msgEmptyQueue'/>");
                }

            }//=default

        });


    });

    registerFormSubmit('addQueueForm', 'dynamic-content');
</script>

</html>
