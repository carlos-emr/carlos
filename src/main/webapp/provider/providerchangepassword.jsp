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
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Security" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.SecurityDao" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%
    if (session.getValue("user") == null)
        response.sendRedirect(request.getContextPath() + "/logout.jsp");

    String errormsg = "";
    if (request.getParameter("errormsg") != null) {
        errormsg = request.getParameter("errormsg");
    }

    String curUser_no = (String) session.getAttribute("user");
    SecurityDao securityDao = SpringUtils.getBean(SecurityDao.class);
    List<Security> ss = securityDao.findByProviderNo(curUser_no);

    Security s = ss.get(0);

    Integer BLocallockset = s.getBLocallockset();
    Integer BRemotelockset = s.getBRemotelockset();
%>

<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%@ page
        import="java.lang.*, java.util.*, java.text.*,java.sql.*, io.github.carlos_emr.*"
        errorPage="/errorpage.jsp" %>

<%!
    OscarProperties op = OscarProperties.getInstance();
%>

<!DOCTYPE html>
<html lang="en">
    <head>
        <%@ include file="/includes/global-head.jspf" %>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/checkPassword.js.jsp"></script>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providerchangepassword.title"/></title>
        <script>
            function setfocus(el) {
                this.focus();
                document.updatepassword.elements[el].focus();
                document.updatepassword.elements[el].select();
            }

            function checkPwdPolicy() {

                var pwd1 = document.updatepassword.mypassword.value;
                var pwd2 = document.updatepassword.confirmpassword.value;

                var pin1 = null;
                var pin2 = null;

                var jsLocallockset = '<%=BLocallockset%>';
                var jsRemotelockset = '<%=BRemotelockset%>';

                if (jsLocallockset == '1' || jsRemotelockset == '1') {
                    pin1 = document.updatepassword.newpin.value;
                    pin2 = document.updatepassword.confirmpin.value;
                }

                if (pwd1 == '' && (pin1 == null || pin1 == '')) {
                    alert('<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providerchangepassword.msgPasswordAndPINBlank"/>');
                    setfocus('mypassword');
                    return false;
                }

                if (pwd1 != "") {
                    if (document.updatepassword.oldpassword.value == "") {
                        alert('<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providerchangepassword.msgCurrPasswordError"/>');
                        setfocus('oldpassword');
                        return false;
                    }
                    if (!validatePassword(pwd1)) {
                        setfocus('mypassword');
                        return false;
                    }
                    if (pwd1 != pwd2) {
                        alert('<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providerchangepassword.msgPasswordConfirmError"/>');
                        setfocus('confirmpassword');
                        return false;
                    }
                }

                if (jsLocallockset == '0' && jsRemotelockset == '0') {
                    return true;
                }

                if (pin1 != "") {
                    if (document.updatepassword.pin.value == "") {
                        alert('<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providerchangepassword.msgCurrPinError"/>');
                        setfocus('pin');
                        return false;
                    }

                    var pin_min_length = <%=op.getProperty("password_pin_min_length")%>;

                    if (pin1.length < pin_min_length) {
                        alert('<fmt:setBundle basename="oscarResources"/><fmt:message key="password.policy.violation.msgPinLengthError"/> ' +
                            pin_min_length + ' <fmt:setBundle basename="oscarResources"/><fmt:message key="password.policy.violation.msgDigits"/>');
                        return false;
                    }

                    if (pin1 != pin2) {
                        alert('<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providerchangepassword.msgPinConfirmError"/>');
                        setfocus('confirmpin');
                        return false;
                    }
                }

                return true;
            }
        </script>
    </head>

    <body onload="setfocus('oldpassword')">
    <div class="container">

        <div class="page-header-bar">
            <h4 class="page-header-title">
                <i class="fas fa-key page-header-icon"></i>&nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providerchangepassword.description"/>
            </h4>
        </div>

        <% if (!errormsg.isEmpty()) { %>
        <div class="alert alert-danger mt-3"><%=Encode.forHtml(errormsg)%></div>
        <% } %>

        <div class="alert alert-info mt-3">
            <fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providerchangepassword.msgInstructions"/>
            <strong><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providerchangepassword.msgUpdate"/></strong>
            <fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providerchangepassword.msgClickButton"/>
        </div>

        <form name="updatepassword" method="post"
              action="providerupdatepassword.jsp" onsubmit="return(checkPwdPolicy())">

            <div class="row mb-3">
                <label class="col-sm-4 col-form-label text-end">
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providerchangepassword.msgEnterOld"/>
                    &nbsp;<strong><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providerchangepassword.formCurrPassword"/>:</strong>
                </label>
                <div class="col-sm-4">
                    <input type="password" name="oldpassword" value="" size="20"
                           maxlength="32" class="form-control form-control-sm">
                </div>
            </div>

            <div class="row mb-3">
                <label class="col-sm-4 col-form-label text-end">
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providerchangepassword.msgChooseNew"/>
                    &nbsp;<strong><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providerchangepassword.formNewPassword"/>:</strong>
                </label>
                <div class="col-sm-4">
                    <input type="password" name="mypassword" value="" size="20"
                           maxlength="32" class="form-control form-control-sm">
                    <small class="text-muted">(<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providerchangepassword.msgAtLeast"/>
                        <%=op.getProperty("password_min_length")%> <fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providerchangepassword.msgSymbols"/>)</small>
                </div>
            </div>

            <div class="row mb-3">
                <label class="col-sm-4 col-form-label text-end">
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providerchangepassword.msgConfirm"/>
                    &nbsp;<strong><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providerchangepassword.formNewPassword"/>:</strong>
                </label>
                <div class="col-sm-4">
                    <input type="password" name="confirmpassword" value="" size="20"
                           maxlength="32" class="form-control form-control-sm">
                    <small class="text-muted">(<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providerchangepassword.msgAtLeast"/>
                        <%=op.getProperty("password_min_length")%> <fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providerchangepassword.msgSymbols"/>)</small>
                </div>
            </div>

            <% if (BLocallockset != null && BRemotelockset != null && (BLocallockset.intValue() == 1 || BRemotelockset.intValue() == 1)) { %>

            <hr>

            <div class="row mb-3">
                <label class="col-sm-4 col-form-label text-end">
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providerchangepassword.msgEnterOld"/>
                    &nbsp;<strong><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providerchangepassword.currentPIN"/>:</strong>
                </label>
                <div class="col-sm-4">
                    <input type="password" name="pin" value="" size="20"
                           maxlength="32" class="form-control form-control-sm">
                </div>
            </div>

            <div class="row mb-3">
                <label class="col-sm-4 col-form-label text-end">
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providerchangepassword.msgChooseNew"/>
                    &nbsp;<strong><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providerchangepassword.newPIN"/>:</strong>
                </label>
                <div class="col-sm-4">
                    <input type="password" name="newpin" value="" size="20"
                           maxlength="32" class="form-control form-control-sm">
                    <small class="text-muted">(<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providerchangepassword.msgAtLeast"/>
                        <%=op.getProperty("password_pin_min_length")%> <fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providerchangepassword.msgSymbols"/>)</small>
                </div>
            </div>

            <div class="row mb-3">
                <label class="col-sm-4 col-form-label text-end">
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providerchangepassword.msgConfirm"/>
                    &nbsp;<strong><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providerchangepassword.newPIN"/>:</strong>
                </label>
                <div class="col-sm-4">
                    <input type="password" name="confirmpin" value="" size="20"
                           maxlength="32" class="form-control form-control-sm">
                    <small class="text-muted">(<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providerchangepassword.msgAtLeast"/>
                        <%=op.getProperty("password_pin_min_length")%> <fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providerchangepassword.msgSymbols"/>)</small>
                </div>
            </div>

            <% } %>

            <div class="d-flex gap-2 mt-3">
                <input type="submit" class="btn btn-primary btn-sm"
                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.providerchangepassword.btnSubmit"/>">
            </div>

        </form>

    </div>
    </body>
</html>
