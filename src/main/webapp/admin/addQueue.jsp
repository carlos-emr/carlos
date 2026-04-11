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

<fmt:setBundle basename="oscarResources"/>
<!DOCTYPE html>

<%@page import="io.github.carlos_emr.carlos.providers.data.*,java.util.*,io.github.carlos_emr.carlos.utility.SpringUtils,io.github.carlos_emr.carlos.commn.dao.QueueDao" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%
    java.util.ResourceBundle addQueueResources =
        java.util.ResourceBundle.getBundle("oscarResources", request.getLocale());
%>

<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_admin" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_admin");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>

<html lang="${pageContext.request.locale.language}">
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

<h3><fmt:message key="admin.addQueue.title"/></h3>

<div class="card card-body bg-body-tertiary">

    <fmt:message key="admin.addQueue.placeholderQueueName" var="placeholderQueueName"/>
    <fmt:message key="admin.addQueue.btnAdd" var="btnAdd"/>
    <form class="d-flex flex-wrap align-items-center gap-2" id="addQueueForm">
        <input type="text" id="newQueueName" class="form-control input-queue" placeholder="${placeholderQueueName}" value=""/>
        <input type="button" class="btn btn-primary" value="${btnAdd}" id="add-btn"/>

        <i class="fa-solid fa-circle-question" style="margin-left:20px;"></i>
    </form>

</div>

<div class="alert">
    <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>
    <div id="addQueueSuccessMsg">
        <strong>Warning!</strong> Best check yo self, you're not looking too good.
    </div>
</div>


<h4><fmt:message key="admin.addQueue.headingExistingQueues"/>:</h4>

					   <ol>
                       <%
                        QueueDao queueDao = (QueueDao) SpringUtils.getBean(QueueDao.class);
                        List<Hashtable> queues=queueDao.getQueues();
                        for(Hashtable qht:queues){
                        %>                            
                                <li><%= Encode.forHtml((String) qht.get("queue")) %></li>
                        <%}%>
                        </ol>
 
</body>

<script type="text/javascript">
    var i18n = {
        docTitle: '<%= Encode.forJavaScript(addQueueResources.getString("admin.addQueue.docTitle")) %>',
        errCannotOverwriteDefault: '<%= Encode.forJavaScript(addQueueResources.getString("admin.addQueue.jsMsgCannotOverwriteDefault")) %>',
        successAdded: '<%= Encode.forJavaScript(addQueueResources.getString("admin.addQueue.jsMsgSuccessAdded")) %>',
        errNotAdded: '<%= Encode.forJavaScript(addQueueResources.getString("admin.addQueue.jsMsgNotAdded")) %>',
        errNotAddedSupport: '<%= Encode.forJavaScript(addQueueResources.getString("admin.addQueue.jsMsgNotAddedSupport")) %>',
        errNameEmpty: '<%= Encode.forJavaScript(addQueueResources.getString("admin.addQueue.jsMsgNameEmpty")) %>',
        labelError: '<%= Encode.forJavaScript(addQueueResources.getString("admin.addQueue.jsLabelError")) %>',
        labelSuccess: '<%= Encode.forJavaScript(addQueueResources.getString("admin.addQueue.jsLabelSuccess")) %>'
    };

    var pageTitle = document.title;
    document.title = i18n.docTitle;

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

                $('#addQueueSuccessMsg').html("<strong>" + i18n.labelError + "</strong> " + i18n.errCannotOverwriteDefault);
            } else {

                if (qn.length > 0) {
                    var data = "method=addNewQueue&newQueueName=" + qn;
                    var url = "${ctx}/documentManager/inboxManage.do";

                    $.ajax({
                        url: url,
                        method: 'POST',
                        data: data,
                        dataType: "json",
                        success: function (data) {
                            $('.alert').addClass('alert-success');
                            $('.alert').show();

                            $('#addQueueSuccessMsg').html("<strong>" + i18n.labelSuccess + "</strong> " + i18n.successAdded.replace('{0}', $('<div/>').text(qn).html()));
                            $('#newQueueName').val("");

                            var json = data.addNewQueue;
                            if (json != null) {
                                if (json == true) {
                                    $('.alert').removeClass('alert-danger');
                                    $('.alert').addClass('alert-success');
                                    $('.alert').show();

                                    $('#addQueueSuccessMsg').html("<strong>" + i18n.labelSuccess + "</strong> " + i18n.successAdded.replace('{0}', $('<div/>').text(qn).html()));
                                    $('#newQueueName').val("");
                                } else {
                                    $('.alert').removeClass('alert-success');
                                    $('.alert').addClass('alert-danger');
                                    $('.alert').show();

                                    $('#addQueueSuccessMsg').html("<strong>" + i18n.labelError + "</strong> " + i18n.errNotAdded.replace('{0}', $('<div/>').text(qn).html()));
                                }
                            }

                        },
                        error: function (data) {
                            $('.alert').removeClass('alert-success');
                            $('.alert').addClass('alert-danger');
                            $('.alert').show();

                            $('#addQueueSuccessMsg').html("<strong>" + i18n.labelError + "</strong> " + i18n.errNotAddedSupport.replace('{0}', $('<div/>').text(qn).html()));
                        }


                    });


                } else {
                    $('.alert').removeClass('alert-success');
                    $('.alert').addClass('alert-danger');
                    $('.alert').show();

                    $('#addQueueSuccessMsg').html("<strong>" + i18n.labelError + "</strong> " + i18n.errNameEmpty);
                }

            }//=default

        });


    });

    registerFormSubmit('addQueueForm', 'dynamic-content');
</script>

</html>
