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

<%@ page errorPage="/WEB-INF/jsp/error/errorpage.jsp" %>

<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">

<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>



<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>

<%@ page import="java.lang.*" %>
<%
    String mcedtUsername = (String) session.getAttribute("mcedtUsername");
    Boolean passwordChangeOutcome = (Boolean) request.getAttribute("isPassChange");
    // A lost commit acknowledgement: the replacement may or may not have been stored.
    boolean passwordChangeUnconfirmed = Boolean.TRUE.equals(request.getAttribute("isPassChangeUnconfirmed"));
%>
<html>
<head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <title>Update MCEDT Password</title>
    <script src="<%= request.getContextPath() %>/library/jquery/jquery-3.7.1.min.js"></script>
    <script src="<%= request.getContextPath() %>/library/jquery/jquery-compat.js"></script>
    <link href="mailbox/css/mcedt.css" rel="stylesheet" type="text/css">


    <script type="text/javascript" charset="utf-8">

        window.onload = function () {

            var isSucess = <%=passwordChangeOutcome%>;
            var isUnconfirmed = <%=passwordChangeUnconfirmed%>;
            if (isUnconfirmed) {
                // Not "unable to change": the database may already hold the new password, so neither
                // password can be assumed to be the active one, and submitting again is not the advice.
                alert('Unable to confirm the EDT password change. Do not assume the previous password is still in effect: check the MCEDT connection with the new password, and contact your administrator if it does not work.');
            } else if (isSucess != null && isSucess == true) {
                alert('EDT Password has been changed successfully.');
                return gobacktoedthome();
                //return true;
            } else if (isSucess === false) {
                alert('Unable to change the EDT password. Please try again or contact your administrator.');
            }

        }

        function gobacktoedthome(control) {
            return submitForm('cancel', control);
        }

        function onSub(control) {


            if (document.getElementById("password").value == "") {
                alert('<fmt:message key="admin.securityrecord.formPassword"/> <fmt:message key="admin.securityrecord.msgIsRequired"/>');
                setfocus('password');
                return false;
            }
            // if (document.getElementById("password").value != "*********" && !validatePassword(document.updatearecord.password.value)) {
            //	setfocus('password');
            //	return false;
            //}
            if (document.getElementById("password").value != document.getElementById("conPassword").value) {
                alert('<fmt:message key="admin.securityrecord.msgPasswordNotConfirmed"/>');
                setfocus('conPassword');
                return false;
            }

            return changePass(control);
        }


        function changePass(control) {
            submitForm("changePassword", control);
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


</head>
<body>
<!-- <div class="show">
       <img class="logo" src="www/img/kai.png"/>
</div> -->
<div class="greyBox">
    <div class="center">
        <div class="row" style="text-align: center;">
            <h1>Update MCEDT Password</h1>
        </div>

        <div class="center" style="width:45%">
            <form action="${pageContext.request.contextPath}/mcedt/kaichpass" method="post" id="form">
                <input id="method" name="method" type="hidden" value=""/>
                <table class="password">
                    <tr>
                        <td>User Name:</td>
                        <td><input name="username" type="text" id="username" class="readonly" value="<%=mcedtUsername%>"
                                   readonly></td>
                    </tr>
                    <tr>
                        <td>New Password:</td>
                        <td><input name="password" type="password" id="password"></td>
                    </tr>
                    <tr>
                        <td>Confirm Password:</td>
                        <td><input name="conPassword" type="password" id="conPassword"></td>
                    </tr>
                    <tr>
                        <td colspan=2>
                            <button type="button" class="noBorder greenBox flatLink font12 rightMargin5"
                                    onclick="return onSub();">Update Password
                            </button>
                            <button id="backedthome" class="noBorder blackBox flatLink font12"
                                    onclick="return gobacktoedthome();">Cancel
                            </button>
                        </td>
                    </tr>
                </table>
            </form>
        </div>
    </div>
</div>
</body>
</html>