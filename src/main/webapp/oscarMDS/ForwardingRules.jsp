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


<%@ page
        import="io.github.carlos_emr.carlos.mds.data.ProviderData, java.util.ArrayList, io.github.carlos_emr.carlos.lab.ForwardingRules, io.github.carlos_emr.CarlosProperties" %>
<%@ page import="org.owasp.encoder.Encode" %>

<%

    ForwardingRules fr = new ForwardingRules();
    String providerNo = request.getParameter("providerNo");
    ArrayList frwdProviders = fr.getProviders(providerNo);
%>

<html>
<head>
    <link href="<%= request.getContextPath() %>/library/bootstrap/5.3.8/css/bootstrap.min.css" rel="stylesheet">
    <link rel="stylesheet" type="text/css" href="<%= request.getContextPath() %>/share/css/global.css"/>
    <link rel="stylesheet" type="text/css" href="encounterStyles.css">
    <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
    <title>Lab Report Forwarding Rules</title>

    <script type="text/javascript" language=javascript>

        function removeProvider(remProviderNo, providerName) {
            var answer = confirm("Are you sure you would like to stop forwarding labs to " + providerName)
            if (answer) {
                document.RULES.operation.value = "remove";
                document.RULES.remProviderNum.value = remProviderNo;
                document.RULES.submit();
                return true;
            } else {
                return false;
            }

        }

        function setActionClear() {
            var answer = confirm("Are you sure you would like to clear the forwarding rules?")
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
            if (autoFileLabs != null && autoFileLabs.equalsIgnoreCase("yes")){%>
            return confirm("Are you sure you would like to update the forwarding rules?")
            <%}else{%>
            if (document.RULES.providerNums.value == '' && document.RULES.status[1].checked && <%= (frwdProviders.size() == 0)%>) {
                alert("You must select a physician to forward your incoming labs to if you wish to automatically file them");
                return false;
            } else {
                return confirm("Are you sure you would like to update the forwarding rules?")
            }
            <%}%>
        }
    </script>
    <link rel="stylesheet" type="text/css" media="all" href="<%= request.getContextPath() %>/share/css/extractedFromPages.css"/>
</head>

<body>
<div class="container">
<form method="post" name="RULES" action="ForwardingRules.do">
    <input type="hidden" name="providerNo" value="<%= Encode.forHtmlAttribute(providerNo) %>">
    <input type="hidden" name="operation" value="update">
    <input type="hidden" name="remProviderNum" value="">

    <div class="page-header-bar">
        <h4 class="page-header-title">Lab Report Forwarding Rules</h4>
        <button type="button" class="btn btn-secondary btn-sm" onclick="window.close();">Back</button>
    </div>

    <div class="py-3">
        <%
            String status = "N";
            if (!fr.isSet(providerNo)) {
        %>
        <%-- No rules set --%>
        <div class="card mb-3">
            <div class="card-header fw-bold">Current Forwarding Rules</div>
            <div class="card-body">
                <p class="text-danger mb-0">There are no forwarding rules set</p>
            </div>
        </div>
        <%
            } else {
                status = fr.getStatus(providerNo);
        %>
        <%-- Current rules --%>
        <div class="card mb-3">
            <div class="card-header fw-bold">Current Forwarding Rules</div>
            <div class="card-body">
                <div class="mb-2">
                    <strong>Incoming lab status:</strong> <%= status.equals("N") ? "New" : "Filed" %>
                </div>
                <%if (frwdProviders != null && frwdProviders.size() > 0) {%>
                <div class="mb-2">
                    <strong>Labs are currently forwarded to:</strong>
                    <ul class="list-unstyled ms-3 mt-1 mb-0">
                        <%for (int i = 0; i < frwdProviders.size(); i++) {%>
                        <li>
                            <%= Encode.forHtml((String) ((ArrayList) frwdProviders.get(i)).get(1)) %>
                            <%= Encode.forHtml((String) ((ArrayList) frwdProviders.get(i)).get(2)) %>
                            <a href="#" class="text-danger ms-2" style="font-size:12px;"
                               onclick="return removeProvider('<%= Encode.forJavaScript((String) ((ArrayList) frwdProviders.get(i)).get(0)) %>', '<%= Encode.forJavaScript((String) ((ArrayList) frwdProviders.get(i)).get(1)) %> <%= Encode.forJavaScript((String) ((ArrayList) frwdProviders.get(i)).get(2)) %>')">Remove</a>
                        </li>
                        <%}%>
                    </ul>
                </div>
                <%} else {%>
                <p class="text-danger mb-2">The incoming labs are not being forwarded</p>
                <%}%>
                <button type="submit" class="btn btn-outline-danger btn-sm" onclick="return setActionClear()">Clear Forwarding Rules</button>
            </div>
        </div>
        <%}%>

        <%-- Update rules --%>
        <div class="card mb-3">
            <div class="card-header fw-bold">Update Forwarding Rules</div>
            <div class="card-body">
                <div class="mb-3">
                    <label class="form-label fw-bold">Set incoming report status:</label>
                    <div class="form-check form-check-inline">
                        <input class="form-check-input" type="radio" name="status" value="N" id="statusNew" <%= status.equals("F") ? "" : "checked" %>>
                        <label class="form-check-label" for="statusNew"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarMDS.search.formReportStatusNew"/></label>
                    </div>
                    <div class="form-check form-check-inline">
                        <input class="form-check-input" type="radio" name="status" value="F" id="statusFiled" <%= status.equals("F") ? "checked" : "" %>>
                        <label class="form-check-label" for="statusFiled">Filed</label>
                    </div>
                </div>
                <div class="mb-3">
                    <label class="form-label fw-bold">Forward incoming reports to the following physicians:</label>
                    <p class="text-muted" style="font-size:12px;">Hold 'Ctrl' to select multiple physicians</p>
                    <select multiple name="providerNums" size="10" class="form-select">
                        <optgroup label="Doctors">
                            <% ArrayList providers = ProviderData.getProviderList();
                                for (int i = 0; i < providers.size(); i++) {
                                    String prov_no = (String) ((ArrayList) providers.get(i)).get(0);
                                    if (!providerNo.equals(prov_no) && !frwdProviders.contains(providers.get(i))) {%>
                            <option value="<%= Encode.forHtmlAttribute(prov_no) %>"><%= Encode.forHtml((String) ((ArrayList) providers.get(i)).get(1)) %> <%= Encode.forHtml((String) ((ArrayList) providers.get(i)).get(2)) %></option>
                            <% }
                            } %>
                        </optgroup>
                    </select>
                </div>
                <button type="submit" class="btn btn-primary" onclick="return confirmUpdate()">Update Forwarding Rules</button>
            </div>
        </div>
    </div>
</form>
</div><%-- close container --%>
</body>
</html>
