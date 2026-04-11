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
<%@page import="org.owasp.encoder.Encode" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ page import="java.util.ResourceBundle" %>


<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>

<%@ page
        import="io.github.carlos_emr.carlos.mds.data.ProviderData, java.util.ArrayList, io.github.carlos_emr.carlos.lab.ForwardingRules, io.github.carlos_emr.CarlosProperties" %>

<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_admin,_admin.misc" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_admin&type=_admin.misc");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
    ResourceBundle labFwdResources = ResourceBundle.getBundle("oscarResources", request.getLocale());
%>
<fmt:setBundle basename="oscarResources"/>
<!DOCTYPE html>
<%

    ForwardingRules fr = new ForwardingRules();
    String providerNo = request.getParameter("providerNo");
    if (providerNo == null)
        providerNo = "0";

    ArrayList frwdProviders = fr.getProviders(providerNo);
%>

<html lang="${pageContext.request.locale.language}">
<head>

    <title><fmt:message key="admin.admin.labFwdRules"/></title>
    <link href="${ctx}/library/bootstrap/5.3.8/css/bootstrap.min.css" rel="stylesheet" type="text/css">
    <script src="${ctx}/library/bootstrap/5.3.8/js/bootstrap.bundle.min.js"></script>
    <link rel="stylesheet" href="${ctx}/css/fontawesome-all.min.css">

    <script type="text/javascript">

        function removeProvider(remProviderNo, providerName) {
            var answer = confirm("<%= Encode.forJavaScript(labFwdResources.getString("admin.labforwardingrules.jsConfirmStopForwarding")) %>" + providerName)
            if (answer) {
                document.RULES.operation.value = "remove";
                document.RULES.remProviderNum.value = remProviderNo;
                return true;
            } else {
                return false;
            }

        }

        function setActionClear() {
            var answer = confirm("<%= Encode.forJavaScript(labFwdResources.getString("admin.labforwardingrules.jsConfirmClearRules")) %>")
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
            alert("<%= Encode.forJavaScript(labFwdResources.getString("admin.labforwardingrules.jsMsgSelectProvider")) %>");
            return false;
            <%}else if(autoFileLabs != null && autoFileLabs.equalsIgnoreCase("yes")){%>
            return confirm("<%= Encode.forJavaScript(labFwdResources.getString("admin.labforwardingrules.jsConfirmUpdateRules")) %>")
            <%}else{%>
            if (document.RULES.providerNums.value == '' && document.RULES.status[1].checked && <%= (frwdProviders.size() == 0)%>) {
                alert("<%= Encode.forJavaScript(labFwdResources.getString("admin.labforwardingrules.jsMsgSelectProviderForward")) %>");
                return false;
            } else {
                return confirm("<%= Encode.forJavaScript(labFwdResources.getString("admin.labforwardingrules.jsConfirmUpdateRules")) %>")
            }
            <%}%>
        }
    </script>

</head>

<body>

<h3><fmt:message key="admin.admin.labFwdRules"/></h3>


<form id="ForwardRulesForm" name="RULES" action="${ctx}/admin/ForwardingRules.do" method="post">

    <input type="hidden" name="operation" value="update">
    <input type="hidden" name="remProviderNum" value="">


    <div class="card card-body bg-body-tertiary">
        <h5><fmt:message key="admin.labforwardingrules.headingSelectProvider"/></h5>
        <fmt:message key="admin.labforwardingrules.msgSelectProviderFor"/>

        <select name="providerNo" id="provider-selection">
            <option value="0"><fmt:message key="admin.labforwardingrules.optionNoneSelected"/></option>
            <% ArrayList providers = ProviderData.getProviderList();
                for (int i = 0; i < providers.size(); i++) {
                    String prov_no = (String) ((ArrayList) providers.get(i)).get(0);%>
            <option value="<%= prov_no %>"
                    <% if (prov_no.equals(providerNo)) {%> <%="selected"%> <%}%>><%= (String) ((ArrayList) providers.get(i)).get(1) %>
                <%= (String) ((ArrayList) providers.get(i)).get(2) %>
            </option>
            <% }%>
        </select>

        <i class="fa-solid fa-circle-question"></i>
        <br>

    </div>


    <div class="card card-body bg-body-tertiary">
        <h5><fmt:message key="admin.labforwardingrules.headingCurrentRules"/></h5>
        <%
            String status = "N";
            if (providerNo.equals("0")) {
        %>
        <p><fmt:message key="admin.labforwardingrules.msgNoProviderSelected"/></p>
        <%
        } else if (!fr.isSet(providerNo)) {%>
        <p class="text-info"><fmt:message key="admin.labforwardingrules.msgNoRulesSet"/></p>
        <%
        } else {
            status = fr.getStatus(providerNo);
        %>


        <%if (frwdProviders != null && frwdProviders.size() > 0) {%>
        <table class="table table-sm table-striped" style="width:44%;">

            <thead>
            <tr>
                <th><fmt:message key="admin.labforwardingrules.thProvider"/></th>
                <th><fmt:message key="admin.labforwardingrules.thIncomingStatus"/></th>
                <th></th>
            </tr>
            </thead>

            <tbody>
                <%for (int i=0; i < frwdProviders.size(); i++){%>
            <tr>
                <td><%= (String) ((ArrayList) frwdProviders.get(i)).get(1) %> <%= (String) ((ArrayList) frwdProviders.get(i)).get(2) %>
                </td>
                <td><%= status.equals("N") ? Encode.forHtml(labFwdResources.getString("admin.labforwardingrules.statusNew")) : Encode.forHtml(labFwdResources.getString("admin.labforwardingrules.statusFiled")) %>
                </td>
                <td>
                    <button type="submit" class="btn btn-sm"
                            onclick="return removeProvider('<%= (String) ((ArrayList) frwdProviders.get(i)).get(0) %>', '<%= Encode.forJavaScript((String) ((ArrayList) frwdProviders.get(i)).get(1)) %> <%= Encode.forJavaScript((String) ((ArrayList) frwdProviders.get(i)).get(2)) %>')"
                            title="<%= Encode.forHtmlAttribute(labFwdResources.getString("admin.labforwardingrules.btnRemoveTitle")) %>"><i class="fa-solid fa-trash"></i> <fmt:message key="admin.labforwardingrules.btnRemove"/>
                    </button>
                </td>
            </tr>

            <br/>
                <%}%>

        </table>
        <%} else {%>


        <div class="alert alert-danger">
            <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>
            <strong><fmt:message key="admin.labforwardingrules.labelWarning"/></strong> <fmt:message key="admin.labforwardingrules.msgLabsNotForwarded"/>
        </div>


        <%}%>
        <br/>
        <button type="submit" class="btn btn-danger" onclick="return setActionClear()"><i class="fa-solid fa-trash"></i> <fmt:message key="admin.labforwardingrules.btnClearAllRules"/>
        </button>

        <%}%>

    </div>


    <div class="card card-body bg-body-tertiary">

        <h5><fmt:message key="admin.labforwardingrules.headingUpdateRules"/></h5>

        <fmt:message key="admin.labforwardingrules.labelSetIncomingStatus"/>
        <input type="radio" name="status" value="N"    <%= status.equals("F") ? "" : "checked" %>> <fmt:message key="oscarMDS.search.formReportStatusNew"/>
        <input type="radio" name="status" value="F" <%= status.equals("F") ? "checked" : "" %>> <fmt:message key="admin.labforwardingrules.statusFiled"/>

        <br/>

        <fmt:message key="admin.labforwardingrules.msgForwardTo"/><br/>

        <small>(<fmt:message key="admin.labforwardingrules.msgCtrlMultiSelect"/>)</small>
        <br/>

        <select multiple name="providerNums" style="height: 200px">
            <fmt:message key="admin.labforwardingrules.optgroupDoctors" var="optgroupDoctors"/>
            <optgroup
                    label="&#160&#160${optgroupDoctors}&#160&#160&#160&#160&#160&#160&#160&#160">
                <% //ArrayList providers = ProviderData.getProviderList();
                    for (int i = 0; i < providers.size(); i++) {
                        String prov_no = (String) ((ArrayList) providers.get(i)).get(0);
                        if (!providerNo.equals(prov_no) && !frwdProviders.contains(providers.get(i))) {%>
                <option value="<%= prov_no %>"><%= (String) ((ArrayList) providers.get(i)).get(1) %>
                    <%= (String) ((ArrayList) providers.get(i)).get(2) %>
                </option>
                <% }
                } %>
            </optgroup>
        </select>

        <br/>
        <fmt:message key="admin.labforwardingrules.btnUpdate" var="btnUpdate"/>
        <input type="submit" class="btn btn-primary" value="${btnUpdate}" onclick="return confirmUpdate()">

    </div>

</form>

</body>

<script>
    var pageTitle = document.title;
    document.title = '<%= Encode.forJavaScript(labFwdResources.getString("admin.labforwardingrules.jsDocTitle")) %>';

    registerFormSubmit('ForwardRulesForm', 'dynamic-content');

    $("#providers-selection").change(function (e) {
        e.preventDefault();
        $("#dynamic-content").load('${ctx}/admin/labforwardingrules.jsp?providerNo=' + $("#providers-selection").val(),
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
