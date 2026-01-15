<!DOCTYPE html>
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
<%
    if (session.getAttribute("user") == null) {
        response.sendRedirect(request.getContextPath() + "/logout.jsp");
    }
%>
<%@ page import="java.util.*,ca.openosp.openo.billings.ca.bc.data.BillingCodeData" %>
<%@ page import="ca.openosp.openo.billings.ca.on.administration.GstControl2Action" %>
<%@ page import="ca.openosp.openo.billings.ca.on.data.JdbcBillingCodeImpl" %>
<%@ page import="java.math.BigDecimal" %>
<%@ page import="ca.openosp.openo.commn.model.BillingService" %>
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="java.text.SimpleDateFormat" %>
<%
    String action = StringUtils.trimToNull(request.getParameter("action")) == null ? "search" : request.getParameter("action"); // add/edit
    String alert = "info";
    String description = StringUtils.trimToEmpty(request.getParameter("description"));
    String percentage = StringUtils.trimToEmpty(request.getParameter("percentage"));
    String gstFlag = StringUtils.trimToNull(request.getParameter("gstFlag")) == null ? "0" : request.getParameter("gstFlag");
    String msg = "Type in a service code and search first to see if it is available.";
    String serviceCode = StringUtils.trimToEmpty(request.getParameter("service_code"));
    String submit = StringUtils.trimToEmpty(request.getParameter("submit"));
    String today = (new SimpleDateFormat("yyyy-MM-dd")).format(new Date());
    String value = request.getParameter("value");

    BillingCodeData bcds = new BillingCodeData();
    JdbcBillingCodeImpl jdbc = new JdbcBillingCodeImpl();

    Properties prop = new Properties();
    String gstPercent = new BigDecimal((new GstControl2Action()).readDatabase().getProperty("gstPercent")).divide(new BigDecimal(100), 0).toString();

    if (action.toLowerCase().startsWith("add")) {
        prop = new Properties();
    }

    if (submit.equals("Save")) {
        if (action.startsWith("edit")) {
            // update the service code
            serviceCode = "A" + serviceCode;
            if (serviceCode.equals(action.substring("edit".length()))) {
                if (jdbc.updateCodeByName(serviceCode, description, value, percentage, request.getParameter("billingservice_date"), gstFlag)) {
                    msg = serviceCode + " is updated.<br>" + "Type in a service code and search first to see if it is available.";
                    action = "search";
                    prop.setProperty("service_code", serviceCode);
                } else {
                    msg = serviceCode + " is <font color='red'>NOT</font> updated. Action failed! Try edit it again.";
                    action = "edit" + serviceCode;
                    prop.setProperty("service_code", serviceCode);
                    prop.setProperty("description", description);
                    prop.setProperty("value", request.getParameter("value"));
                    prop.setProperty("billingservice_date", request.getParameter("billingservice_date"));
                    prop.setProperty("gstFlag", gstFlag);
                }
            } else {
                msg = "You can <font color='red'>NOT</font> save the service code - " + serviceCode + ". Please search the service code first.";
                action = "search";
                prop.setProperty("service_code", serviceCode);
            }
        } else if (action.startsWith("add")) {
            serviceCode = "A" + serviceCode;
            if (serviceCode.equals(action.substring("add".length()))) {
                if (bcds.addBillingCode(serviceCode, description, value)) {
                    msg = serviceCode + " is added.<br>" + "Type in a service code and search first to see if it is available.";
                    action = "search";
                    prop.setProperty("service_code", serviceCode);
                } else {
                    msg = serviceCode + " is not added. Action failed! Try edit it again.";
                    action = "add" + serviceCode;
                    prop.setProperty("service_code", serviceCode);
                    prop.setProperty("description", description);
                    prop.setProperty("value", request.getParameter("value"));
                    prop.setProperty("billingservice_date", request.getParameter("billingservice_date"));
                    prop.setProperty("gstFlag", gstFlag);
                    alert = "error";
                }
            } else {
                msg = "You can not save the service code - " + serviceCode + ". Please search the service code first.";
                action = "search";
                prop.setProperty("service_code", serviceCode);
                alert = "error";
            }
        } else {
            msg = "You can not save the service code. Please search the service code first.";
            alert = "error";
        }
    } else if (submit.equals("Search")) {
        // check the input data
        if (serviceCode.isEmpty()) {
            msg = "Please type in a right service code.";
        } else {
            if (!serviceCode.startsWith("A")) {
                serviceCode = "A" + serviceCode;
            }
            List<String> ls = jdbc.getBillingCodeAttr(serviceCode);
            if (ls.size() > 0) {
                prop.setProperty("service_code", serviceCode);
                prop.setProperty("description", ls.get(1));
                prop.setProperty("value", ls.get(2));
                prop.setProperty("percentage", ls.get(3));
                prop.setProperty("billingservice_date", ls.get(4));
                prop.setProperty("gstFlag", ls.get(5));
                msg = "You can edit the service code.";
                action = "edit" + serviceCode;
            } else {
                prop.setProperty("service_code", serviceCode);
                msg = "It is a NEW service code. You can add it.";
                action = "add" + serviceCode;
            }
        }
    } else if (submit.equals("Delete")) {
        if (serviceCode.isEmpty()) {
            msg = "Please type in a right service code.";
        } else {
            serviceCode = "A" + serviceCode;
            if (jdbc.deletePrivateCode(serviceCode)) {
                msg = serviceCode + " is deleted.<br>" + "Type in a service code and search first to see if it is available.";
                action = "search";
                prop.setProperty("service_code", "A");
            } else {
                msg = serviceCode + " could not be deleted, please try again.<br>" + "Type in a service code and search first to see if it is available.";
                action = "search";
                prop.setProperty("service_code", "A");
            }
        }
    }

    // List<BillingService> sL = bcds.findAllPrivateCodes();
    List<BillingService> sL = new ArrayList<>();
%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%@ taglib uri="http://displaytag.sf.net" prefix="display" %>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>

<html>
    <head>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.ManagePrivFrm"/></title>
        <script src="<%=request.getContextPath()%>/csrfguard" type="text/javascript"></script>

        <link href="<%=request.getContextPath() %>/css/bootstrap.css" rel="stylesheet">
        <link href="<%=request.getContextPath() %>/css/datepicker.css" rel="stylesheet">
    </head>

    <body>
    <h3><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.ManagePrivFrm"/></h3>
    <div class="container-fluid">
        <div class="select-code well">
            <form method="post" name="selectCode" action="billingBCEditPrivateCode.jsp" class="form-inline">
                <label for="service_code_select">Select Code to edit:</label><br/>
                <select name="service_code" id="service_code_select">
                    <option selected="selected" value="">- choose one -</option>
                    <%
                        for (BillingService s : sL) {
                            String strCode = s.getServiceCode();
                            String strDesc = StringUtils.trimToEmpty(s.getDescription());
                            strDesc = strDesc.length() > 30 ? strDesc.substring(0, 30) : strDesc;
                    %>
                    <option value="<%=strCode%>"><%=(strCode + "| " + strDesc)%>
                    </option>
                    <%}%>
                </select>
                <input type="hidden" name="submit" value="Search">
                <input class="btn" type="submit" name="action" value="Edit">
            </form>
        </div>

        <div class="manage-code well">
            <form method="post" name="baseurl" action="billingBCEditPrivateCode.jsp">
                <div class="alert alert-<%=alert%>">
                    <%=msg%>
                </div>

                <label for="service_code">
                    Private Code <small>(e.g. A0010)</small><br/>
                    <small>Private Codes will be prefixed with 'A' by default</small>
                </label>
                <div class="input-append input-prepend">
                    <span class="add-on">A</span>
                    <input type="text" name="service_code" id="service_code"
                           value="<%=prop.getProperty("service_code", "?").substring(1)%>" class="span2" maxlength='10'
                           onblur="upCaseCtrl(this)" required/>
                    <button type="submit" name="submit" class="btn btn-primary" onclick="return onSearch();"
                            value="Search">Search
                    </button>
                </div>

                <label for="description">Description</label>
                <input type="text" name="description" id="description" value="<%=prop.getProperty("description", "")%>"
                       size='50'><br/>

                <label for="value">Fee <small>(format: xx.xx, e.g. 18.20)</small></label>
                <input type="text" name="value" id="value" value="<%=prop.getProperty("value", "")%>" size='8'
                       maxlength='8'>

                <label for="gstFlag">
                    <input type="checkbox" name="gstFlag" id="gstFlag"
                           value="1" <%="1".equals(prop.getProperty("gstFlag", "")) ? "checked" : ""%> />
                    Add GST
                </label>

                <label class="date" for="billingservice_date">Issued Date <small>(effective date)</small></label>
                <% String billingServiceDate = prop.getProperty("billingservice_date") != null ? prop.getProperty("billingservice_date") : today; %>
                <div class="input-append">
                    <input style="width:90px" name="billingservice_date" id="billingservice_date" data-date="today()"
                           data-date-format="yyyy-mm-dd" size="16" type="text" value="<%=billingServiceDate%>"
                           pattern="^\d{4}-((0\d)|(1[012]))-(([012]\d)|3[01])$" readonly>
                    <span class="btn"><i class="icon-calendar"></i></span>
                </div>

                <div>
                    <input type="hidden" name="action" value='<%=action%>'/>
                    <%if (action.startsWith("edit")) { %>
                    <input class="btn" type="submit" name="submit" value="Delete" onclick="return onDelete();"/>
                    <%}%>
                    <input class="btn" type="submit" name="submit"
                           value="<fmt:setBundle basename="oscarResources"/><fmt:message key="admin.resourcebaseurl.btnSave"/>" onclick="return onSave();"/>
                </div>
            </form>
        </div>
    </div>

    <script src="<%=request.getContextPath() %>/js/jquery-1.9.1.min.js"></script>
    <script src="<%=request.getContextPath() %>/js/bootstrap.min.js"></script>
    <script src="<%=request.getContextPath() %>/js/bootstrap-datepicker.js"></script>
    <script src="<%=request.getContextPath() %>/billing/editPrivateCode.js"></script>

    </body>
    <script type="text/javascript">
        <!--
        function setfocus() {
            this.focus();
            document.forms[1].service_code.focus();
            document.forms[1].service_code.select();
            let gstFlag = "<%=prop.getProperty("gstFlag")%>";

            if (gstFlag === "1") {
                document.getElementById("gstFlag").setAttribute("checked", "checked");
            } else {
                document.getElementById("gstFlag").removeAttribute("checked")
            }
        }

        //-->

        $(function () {
            $('#billingservice_date').datepicker();
        });
    </script>
</html>
