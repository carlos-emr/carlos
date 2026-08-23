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
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>


<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>

<%@ page
        import="io.github.carlos_emr.carlos.mds.data.ProviderData, java.util.ArrayList, io.github.carlos_emr.carlos.lab.ForwardingRules, io.github.carlos_emr.CarlosProperties, java.util.ResourceBundle" %>

<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_admin,_admin.misc" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_admin&type=_admin.misc");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>


<%

    ForwardingRules fr = new ForwardingRules();
    String providerNo = request.getParameter("providerNo");
    if (providerNo == null)
        providerNo = "0";

    ArrayList frwdProviders = fr.getProviders(providerNo);
    ResourceBundle oscarRec = ResourceBundle.getBundle("oscarResources", request.getLocale());
%>

<html>
<head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>

    <title><fmt:message key="admin.admin.labFwdRules"/></title>
    <link href="${ctx}/library/bootstrap/5.3.8/css/bootstrap.min.css" rel="stylesheet" type="text/css">
    <script type="text/javascript" src="${ctx}/library/jquery/jquery-3.7.1.min.js"></script>
    <script type="text/javascript" src="${ctx}/library/jquery/jquery-compat.js"></script>
    <script src="${ctx}/library/bootstrap/5.3.8/js/bootstrap.bundle.min.js"></script>
    <link rel="stylesheet" href="${ctx}/css/fontawesome-all.min.css">

    <script type="text/javascript">

        function removeProvider(remProviderNo, providerName) {
            var answer = confirm("<fmt:message key='admin.labFwdRules.confirmRemovePrefix'/>" + providerName)
            if (answer) {
                document.RULES.operation.value = "remove";
                document.RULES.remProviderNum.value = remProviderNo;
                return true;
            } else {
                return false;
            }

        }

        function setActionClear() {
            var answer = confirm("<fmt:message key='admin.labFwdRules.confirmClear'/>")
            if (answer) {
                document.RULES.operation.value = "clear";
                return true;
            } else {
                return false;
            }
        }

        function confirmUpdate() {
            <%
            CarlosProperties props = CarlosProperties.getInstance();
            String autoFileLabs = props.getProperty("AUTO_FILE_LABS");
            if (providerNo.equals("0")){%>
            alert("<fmt:message key='admin.labFwdRules.mustSelectProvider'/>");
            return false;
            <%}else if(autoFileLabs != null && autoFileLabs.equalsIgnoreCase("yes")){%>
            return confirm("<fmt:message key='admin.labFwdRules.confirmUpdate'/>")
            <%}else{%>
            if (document.RULES.providerNums.value == '' && document.RULES.status[1].checked && <%= (frwdProviders.size() == 0)%>) {
                alert("<fmt:message key='admin.labFwdRules.mustSelectForwardingProvider'/>");
                return false;
            } else {
                return confirm("<fmt:message key='admin.labFwdRules.confirmUpdate'/>")
            }
            <%}%>
        }
    </script>

</head>

<body>

<h3><fmt:message key="admin.admin.labFwdRules"/></h3>


<form id="ForwardRulesForm" name="RULES" action="${ctx}/admin/ForwardingRules" method="post">

    <input type="hidden" name="operation" value="update">
    <input type="hidden" name="remProviderNum" value="">


    <div class="card card-body bg-body-tertiary">
        <h5><fmt:message key="oscarMDS.selectProvider.title"/></h5>
        <fmt:message key="admin.labFwdRules.instructions"/>

        <select name="providerNo" id="provider-selection">
            <option value="0"><fmt:message key="admin.manageCodeStyles.NoneSelected"/></option>
            <% ArrayList providers = ProviderData.getProviderList();
                for (int i = 0; i < providers.size(); i++) {
                    String prov_no = (String) ((ArrayList) providers.get(i)).get(0);%>
            <option value="<carlos:encode value='<%= prov_no %>' context="htmlAttribute"/>"
                    <% if (prov_no.equals(providerNo)) {%> <%="selected"%> <%}%>><carlos:encode value='<%= (String) ((ArrayList) providers.get(i)).get(1) %>' context="html"/>
                <carlos:encode value='<%= (String) ((ArrayList) providers.get(i)).get(2) %>' context="html"/>
            </option>
            <% }%>
        </select>

        <i class="fa-solid fa-circle-question"></i>
        <br>

    </div>


    <div class="card card-body bg-body-tertiary">
        <h5><fmt:message key="admin.labFwdRules.currentRules"/></h5>
        <%
            String status = "N";
            if (providerNo.equals("0")) {
        %>
        <p><fmt:message key="admin.labFwdRules.noProviderSelected"/></p>
        <%
        } else if (!fr.isSet(providerNo)) {%>
        <p class="text-info"><fmt:message key="admin.labFwdRules.noneConfigured"/></p>
        <%
        } else {
            status = fr.getStatus(providerNo);
        %>


        <%if (frwdProviders != null && frwdProviders.size() > 0) {%>
        <table class="table table-sm table-striped" style="width:44%;">

            <thead>
            <tr>
                <th><fmt:message key="admin.labFwdRules.provider"/></th>
                <th><fmt:message key="admin.labFwdRules.incomingStatus"/></th>
                <th></th>
            </tr>
            </thead>

            <tbody>
                <%for (int i=0; i < frwdProviders.size(); i++){%>
            <tr>
                <td><carlos:encode value='<%= (String) ((ArrayList) frwdProviders.get(i)).get(1) %>' context="html"/> <carlos:encode value='<%= (String) ((ArrayList) frwdProviders.get(i)).get(2) %>' context="html"/>
                </td>
                <td><%= status.equals("N") ? oscarRec.getString("oscarMDS.search.formReportStatusNew") : oscarRec.getString("inbox.inboxmanager.msgFiled") %>
                </td>
                <td>
                    <button type="submit" class="btn btn-sm"
                            onclick="return removeProvider('<carlos:encode value='<%= (String) ((ArrayList) frwdProviders.get(i)).get(0) %>' context="javaScriptAttribute"/>', '<carlos:encode value='<%= (String) ((ArrayList) frwdProviders.get(i)).get(1) %>' context="javaScriptAttribute"/> <carlos:encode value='<%= (String) ((ArrayList) frwdProviders.get(i)).get(2) %>' context="javaScriptAttribute"/>')"
                            title="<fmt:message key='admin.labFwdRules.removeTitle'/>"><i class="fa-solid fa-trash"></i> <fmt:message key="admin.labFwdRules.remove"/>
                    </button>
                </td>
            </tr>

            <br/>
                <%}%>

        </table>
        <%} else {%>


        <div class="alert alert-danger">
            <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>
            <strong><fmt:message key="global.warning"/></strong> <fmt:message key="admin.labFwdRules.notForwarding"/>
        </div>


        <%}%>
        <br/>
        <button type="submit" class="btn btn-danger" onclick="return setActionClear()"><i class="fa-solid fa-trash"></i> <fmt:message key="global.clear"/>
            <fmt:message key="admin.labFwdRules.allForwardingRules"/>
        </button>

        <%}%>

    </div>


    <div class="card card-body bg-body-tertiary">

        <h5><fmt:message key="admin.labFwdRules.updateRules"/></h5>

        <fmt:message key="admin.labFwdRules.setIncomingStatus"/>
        <input type="radio" name="status" value="N"    <%= status.equals("F") ? "" : "checked" %>> <fmt:message key="oscarMDS.search.formReportStatusNew"/>
        <input type="radio" name="status" value="F" <%= status.equals("F") ? "checked" : "" %>> <fmt:message key="inbox.inboxmanager.msgFiled"/>

        <br/>

        <fmt:message key="admin.labFwdRules.forwardTo"/><br/>

        <small><fmt:message key="admin.labFwdRules.holdCtrl"/></small>
        <br/>

        <select multiple name="providerNums" style="height: 200px">
            <optgroup
                    label="&#160&#160<%= oscarRec.getString("admin.labFwdRules.doctors") %>&#160&#160&#160&#160&#160&#160&#160&#160">
                <% //ArrayList providers = ProviderData.getProviderList();
                    for (int i = 0; i < providers.size(); i++) {
                        String prov_no = (String) ((ArrayList) providers.get(i)).get(0);
                        if (!providerNo.equals(prov_no) && !frwdProviders.contains(providers.get(i))) {%>
                <option value="<carlos:encode value='<%= prov_no %>' context="htmlAttribute"/>"><carlos:encode value='<%= (String) ((ArrayList) providers.get(i)).get(1) %>' context="html"/>
                    <carlos:encode value='<%= (String) ((ArrayList) providers.get(i)).get(2) %>' context="html"/>
                </option>
                <% }
                } %>
            </optgroup>
        </select>

        <br/>
        <input type="submit" class="btn btn-primary" value="<fmt:message key='global.update'/>" onclick="return confirmUpdate()">

    </div>

</form>

</body>

<script>
    var pageTitle = document.title;
    document.title = 'Administration Panel | Lab Forwarding Rules';

    if (typeof registerFormSubmit === 'function') {
        registerFormSubmit('ForwardRulesForm', 'dynamic-content');
    }

    $("#providers-selection").change(function (e) {
        e.preventDefault();
        $("#dynamic-content").load('${ctx}/admin/labForwardingRules?providerNo=' + $("#providers-selection").val(),
            function (response, status, xhr) {
                if (status == "error") {
                    var msg = "Sorry but there was an error: ";
                    $("#dynamic-content").html(msg + xhr.status + " " + xhr.statusText);
                }
            }
        );
    });


</script>
</html>
