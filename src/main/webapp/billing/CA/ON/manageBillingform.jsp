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

--%>
<!DOCTYPE html>
<%
    String user_no = (String) session.getAttribute("user");
    String asstProvider_no = "";
    String color = "";
    String premiumFlag = "";
    String service_form = "", service_name = "";
%>

<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>


<%@ page import="java.util.*, java.sql.*, ca.openosp.*, java.net.*" %>
<%@ include file="/admin/dbconnection.jsp" %>
<%@ page import="ca.openosp.openo.utility.SpringUtils" %>
<%@ page import="ca.openosp.openo.commn.model.CtlBillingService" %>
<%@ page import="ca.openosp.openo.commn.dao.CtlBillingServiceDao" %>
<%@ page import="ca.openosp.openo.commn.model.CtlDiagCode" %>
<%@ page import="ca.openosp.openo.commn.dao.CtlDiagCodeDao" %>
<%@ page import="ca.openosp.openo.commn.model.CtlBillingServicePremium" %>
<%@ page import="ca.openosp.openo.commn.dao.CtlBillingServicePremiumDao" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%
    CtlBillingServiceDao ctlBillingServiceDao = SpringUtils.getBean(CtlBillingServiceDao.class);
    CtlDiagCodeDao ctlDiagCodeDao = SpringUtils.getBean(CtlDiagCodeDao.class);
    CtlBillingServicePremiumDao ctlBillingServicePremiumDao = SpringUtils.getBean(CtlBillingServicePremiumDao.class);

%>


<%
    String clinicview = request.getParameter("billingform") == null ? oscarVariables.getProperty("default_view") : request.getParameter("billingform");
    String reportAction = request.getParameter("reportAction") == null ? "" : request.getParameter("reportAction");
%>
<html>
    <head>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="billing.manageBillingform.title"/></title>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <link href="${pageContext.request.contextPath}/css/bootstrap.css" rel="stylesheet" type="text/css">
        <!-- Bootstrap 2.3.1 -->

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
                    form.action = "<%= request.getContextPath() %>/billing/CA/ON/dbManageBillingform_add.jsp";
                    form.submit();
                }
            }

            function validateServiceType() {
                if (document.servicetypeform.typeid.value == "MFP") {
                    alert("<fmt:setBundle basename="oscarResources"/><fmt:message key="billing.manageBillingform.msgIDExists"/>");
                    return false;
                }

                if (document.servicetypeform.typeid.value == '') {
                    alert("<fmt:setBundle basename="oscarResources"/><fmt:message key="billing.manageBillingform.btnManage.msgRequiredField"/>");
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
                url = "manageBillingform_billtype.jsp";
                pars = "type_id=" + stype + "&type_name=" + stype_name;

                fetch(url + "?" + pars, {method: "get"})
                    .then(function (response) {
                        return response.text();
                    }).then(function (data) {
                    document.getElementById("manage_type").innerHTML = data;
                });
                showManageType(true);
            }

            function onUnbilled(url) {
                if (confirm("<fmt:setBundle basename="oscarResources"/><fmt:message key="billing.manageBillingform.msgDeleteBillingConfirm"/>")) {
                    popupPage(700, 720, url);
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
                url = "dbManageBillingform_billtype.jsp";
                pars = "?servicetype=" + id + "&billtype_old=" + oldtype + "&billtype=" + newtype;
                popupPage(700, 720, url + pars);
            }

        </script>
    </head>
    <body onload="showManageType(false);">
    <h4><b>oscar<fmt:setBundle basename="oscarResources"/><fmt:message key="billing.manageBillingform.msgBilling"/></h4>

    <form name="serviceform" method="post" action="manageBillingform.jsp">

        <div class="well">
            <table width="100%">
                <tr>
                    <td style="width:30%; text-align:right">
                        <input type="radio" name="reportAction" value="servicecode"
                                <%=reportAction.equals("servicecode")?"checked":""%>> <fmt:setBundle basename="oscarResources"/><fmt:message key="billing.manageBillingform.formServiceCode"/>
                        <input type="radio" name="reportAction" value="dxcode"
                                <%=reportAction.equals("dxcode")?"checked":""%>> <fmt:setBundle basename="oscarResources"/><fmt:message key="billing.manageBillingform.formDxCode"/></td>
                    <td style="width:40%; text-align: center">
                        <div style="align:right">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="billing.manageBillingform.formSelectForm"/>&nbsp;&nbsp;
                            <select name="billingform">
                                <option value="000" <%=clinicview.equals("000") ? "selected" : ""%>><fmt:setBundle basename="oscarResources"/><fmt:message key="billing.manageBillingform.formAddDelete"/></option>
                                <option value="***" <%=clinicview.equals("***") ? "selected" : ""%>><fmt:setBundle basename="oscarResources"/><fmt:message key="billing.manageBillingform.formManagePremium"/></option>

                                <%
                                    String serviceType = "";
                                    String serviceTypeName = "";
                                    List<Object[]> billingServices = ctlBillingServiceDao.findServiceTypes();

                                    for (Object[] billingService : billingServices) {
                                        serviceType = String.valueOf(billingService[0]);
                                        serviceTypeName = String.valueOf(billingService[1]);
                                %>
                                <option value="<%=serviceType%>"
                                        <%=clinicview.equals(serviceType) ? "selected" : ""%>><%=serviceTypeName%>
                                </option>
                                <%
                                    }
                                %>
                            </select></div>
                    </td>
                    <td style="width:30%;">
                        <input type="submit" name="Submit" class="btn"
                               value="<fmt:setBundle basename="oscarResources"/><fmt:message key="billing.manageBillingform.btnManage"/>">
                    </td>
                </tr>
            </table>
    </form>
    <br>
    <%
        if (clinicview.compareTo("000") == 0) { %>
    <%@ include file="manageBillingform_add.jspf" %>
    <%} else if (clinicview.compareTo("***") == 0) { %>
    <%@ include file="manageBillingform_premium.jspf" %>
    <%} else if (reportAction.compareTo("") == 0 || reportAction == null) { %>
    <p>&nbsp;</p>
    <%} else if (reportAction.compareTo("servicecode") == 0) { %>
    <%@ include file="manageBillingform_service.jspf" %>
    <%} else if (reportAction.compareTo("dxcode") == 0) { %>
    <%@ include file="manageBillingform_dx.jspf" %>
    <%
        }
    %>
    </div>

    </body>
</html>