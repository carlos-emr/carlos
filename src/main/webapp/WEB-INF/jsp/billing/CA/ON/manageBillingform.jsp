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
<!DOCTYPE html>
<%@ page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.CtlBillingServiceDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.CtlDiagCodeDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.CtlBillingServicePremiumDao" %>
<%@ page import="io.github.carlos_emr.carlos.billings.ca.on.data.ManageBillingformViewModel" %>
<%@ page import="io.github.carlos_emr.carlos.billings.ca.on.pageUtil.ManageBillingformDataAssembler" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>

<%@ include file="/WEB-INF/jsp/admin/dbconnection.jsp" %>

<%--
  Defensive model-resolver: ensures ${manageBillingformModel} is set on the
  request even on the unlikely path where this JSP is reached without going
  through ManageBillingform2Action. Re-runs the action's _admin.billing w
  privilege check for parity, and exposes the legacy DAO locals
  (ctlBillingServiceDao, ctlDiagCodeDao, ctlBillingServicePremiumDao) to the
  unmigrated manageBillingform_add.jspf fragment which still reads them in
  scriptlet bodies (out of scope for this refactor pass).
--%>
<%
    CtlBillingServiceDao ctlBillingServiceDao = SpringUtils.getBean(CtlBillingServiceDao.class);
    CtlDiagCodeDao ctlDiagCodeDao = SpringUtils.getBean(CtlDiagCodeDao.class);
    CtlBillingServicePremiumDao ctlBillingServicePremiumDao = SpringUtils.getBean(CtlBillingServicePremiumDao.class);
    if (request.getAttribute("manageBillingformModel") == null) {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null) {
            throw new SecurityException("manageBillingform.jsp fallback: missing session");
        }
        io.github.carlos_emr.carlos.managers.SecurityInfoManager __secMgr;
        try {
            __secMgr = SpringUtils.getBean(io.github.carlos_emr.carlos.managers.SecurityInfoManager.class);
        } catch (RuntimeException __springEx) {
            io.github.carlos_emr.carlos.utility.MiscUtils.getLogger().error(
                    "manageBillingform.jsp fallback: SecurityInfoManager bean lookup failed", __springEx);
            throw new SecurityException("manageBillingform.jsp fallback: privilege check unavailable", __springEx);
        }
        if (!__secMgr.hasPrivilege(loggedInInfo, "_admin.billing", "w", null)) {
            throw new SecurityException("manageBillingform.jsp fallback: missing required sec object (_admin.billing)");
        }
        request.setAttribute("manageBillingformModel",
                new ManageBillingformDataAssembler().assemble(request));
    }
%>
<html>
    <head>
        <title><fmt:message key="billing.manageBillingform.title"/></title>
        <script type="text/javascript" src="${pageContext.request.contextPath}/js/global.js"></script>
        <link href="${pageContext.request.contextPath}/library/bootstrap/5.3.8/css/bootstrap.min.css" rel="stylesheet" type="text/css">
        <!-- Bootstrap -->

        <script>

            function selectprovider(s) {
                if (self.location.href.lastIndexOf("&providerview=") > 0) a = self.location.href.substring(0, self.location.href.lastIndexOf("&providerview="));
                else a = self.location.href;
                self.location.href = a + "&providerview=" + s.options[s.selectedIndex].value;
            }

            function openBrWindow(theURL, winName, features) {
                window.open(theURL, winName, features);
            }

            function setfocus() {
                this.focus();
                document.ADDAPPT.keyword.focus();
                document.ADDAPPT.keyword.select();
            }

            function valid(form) {
                if (validateServiceType(form)) {
                    form.action = "${pageContext.request.contextPath}/billing/CA/ON/DbManageBillingformAdd";
                    form.submit();
                }
            }

            function validateServiceType() {
                if (document.servicetypeform.typeid.value == "MFP") {
                    alert("<fmt:message key="billing.manageBillingform.msgIDExists"/>");
                    return false;
                }

                if (document.servicetypeform.typeid.value == '') {
                    alert("<fmt:message key="billing.manageBillingform.btnManage.msgRequiredField"/>");
                    return false;
                }
                return true;
            }

            function refresh() {
                var u = self.location.href;
                if (u.lastIndexOf("view=1") > 0) {
                    self.location.href = u.substring(0, u.lastIndexOf("view=1")) + "view=0" + u.substring(eval(u.lastIndexOf("view=1") + 6));
                } else {
                    history.go(0);
                }
            }

            function manageType(stype, stype_name) {
                url = "${pageContext.request.contextPath}/billing/CA/ON/ManageBillingformBilltype";
                pars = "type_id=" + stype + "&type_name=" + stype_name;

                fetch(url + "?" + pars, {method: "get"})
                    .then(function (response) {
                        return response.text();
                    }).then(function (data) {
                    document.getElementById("manage_type").innerHTML = data;
                });
                showManageType(true);
            }

            function postToPopup(action, params, winName, w, h) {
                var form = document.createElement('form');
                form.method = 'POST';
                form.action = action;
                form.target = winName;
                for (var key in params) {
                    var input = document.createElement('input');
                    input.type = 'hidden';
                    input.name = key;
                    input.value = params[key];
                    form.appendChild(input);
                }
                window.open('', winName, 'width=' + w + ',height=' + h);
                document.body.appendChild(form);
                form.submit();
                document.body.removeChild(form);
            }

            function onUnbilled(servicetype) {
                if (confirm("<fmt:message key="billing.manageBillingform.msgDeleteBillingConfirm"/>")) {
                    postToPopup('${pageContext.request.contextPath}/billing/CA/ON/DbManageBillingformDelete',
                        {servicetype: servicetype}, 'deletePopup', 700, 720);
                }
            }

            function showManageType(cmd) {
                var el = document.getElementById("manage_type");
                if (el == null) {
                    return;
                }
                if (cmd) el.style.display = "block";
                else el.style.display = "none";
            }

            function manageBillType(id, oldtype, newtype) {
                postToPopup('${pageContext.request.contextPath}/billing/CA/ON/DbManageBillingformBilltype',
                    {servicetype: id, billtype_old: oldtype, billtype: newtype},
                    'billtypePopup', 700, 720);
            }

        </script>
    </head>
    <body onload="showManageType(false);">
    <h4><b>oscar<fmt:message key="billing.manageBillingform.msgBilling"/></h4>

    <form name="serviceform" method="post" action="${pageContext.request.contextPath}/billing/CA/ON/ManageBillingform">

        <div class="card card-body bg-body-tertiary">
            <table width="100%">
                <tr>
                    <td style="width:30%; text-align:right">
                        <input type="radio" name="reportAction" value="servicecode"
                               <c:if test="${manageBillingformModel.reportAction eq 'servicecode'}">checked</c:if>>
                        <fmt:message key="billing.manageBillingform.formServiceCode"/>
                        <input type="radio" name="reportAction" value="dxcode"
                               <c:if test="${manageBillingformModel.reportAction eq 'dxcode'}">checked</c:if>>
                        <fmt:message key="billing.manageBillingform.formDxCode"/></td>
                    <td style="width:40%; text-align: center">
                        <div style="align:right">
                            <fmt:message key="billing.manageBillingform.formSelectForm"/>&nbsp;&nbsp;
                            <select name="billingform">
                                <option value="000" <c:if test="${manageBillingformModel.clinicView eq '000'}">selected</c:if>><fmt:message key="billing.manageBillingform.formAddDelete"/></option>
                                <option value="***" <c:if test="${manageBillingformModel.clinicView eq '***'}">selected</c:if>><fmt:message key="billing.manageBillingform.formManagePremium"/></option>
                                <c:forEach var="opt" items="${manageBillingformModel.serviceTypes}">
                                    <option value="<carlos:encode value='${opt.code}' context='htmlAttribute'/>"
                                            <c:if test="${manageBillingformModel.clinicView eq opt.code}">selected</c:if>>
                                        <carlos:encode value='${opt.name}' context='html'/>
                                    </option>
                                </c:forEach>
                            </select></div>
                    </td>
                    <td style="width:30%;">
                        <input type="submit" name="Submit" class="btn btn-secondary"
                               value="<fmt:message key="billing.manageBillingform.btnManage"/>">
                    </td>
                </tr>
            </table>
    </form>
    <br>
    <c:choose>
        <c:when test="${manageBillingformModel.clinicView eq '000'}">
            <%@ include file="manageBillingform_add.jspf" %>
        </c:when>
        <c:when test="${manageBillingformModel.clinicView eq '***'}">
            <%@ include file="manageBillingform_premium.jspf" %>
        </c:when>
        <c:when test="${empty manageBillingformModel.reportAction}">
            <p>&nbsp;</p>
        </c:when>
        <c:when test="${manageBillingformModel.reportAction eq 'servicecode'}">
            <%@ include file="manageBillingform_service.jspf" %>
        </c:when>
        <c:when test="${manageBillingformModel.reportAction eq 'dxcode'}">
            <%@ include file="manageBillingform_dx.jspf" %>
        </c:when>
    </c:choose>
    </div>

    </body>
</html>