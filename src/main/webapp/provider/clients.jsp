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
<%@ page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="java.text.SimpleDateFormat" %>
<%@ page import="java.util.*" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.ServiceRequestTokenDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.ServiceAccessTokenDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.ServiceClientDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.ServiceClient" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.ServiceRequestToken" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.ServiceAccessToken" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%
    SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
    String providerNo = loggedInInfo.getLoggedInProviderNo();

    ServiceRequestTokenDao serviceRequestTokenDao = SpringUtils.getBean(ServiceRequestTokenDao.class);
    ServiceAccessTokenDao serviceAccessTokenDao = SpringUtils.getBean(ServiceAccessTokenDao.class);
    ServiceClientDao serviceClientDao = SpringUtils.getBean(ServiceClientDao.class);

    List<ServiceRequestToken> requestTokens = new ArrayList<ServiceRequestToken>();
    List<ServiceAccessToken> accessTokens = new ArrayList<ServiceAccessToken>();

    for (ServiceRequestToken t : serviceRequestTokenDao.findAll()) {
        if (t.getProviderNo() != null && t.getProviderNo().equals(providerNo)) {
            requestTokens.add(t);
        }
    }
    for (ServiceAccessToken t : serviceAccessTokenDao.findAll()) {
        if (t.getProviderNo() != null && t.getProviderNo().equals(providerNo)) {
            accessTokens.add(t);
        }
    }

    Map<Integer, ServiceClient> clientMap = new HashMap<Integer, ServiceClient>();
    for (ServiceClient c : serviceClientDao.findAll()) {
        clientMap.put(c.getId(), c);
    }
%>

<!DOCTYPE html>
<html lang="en">
    <head>
        <%@ include file="/includes/global-head.jspf" %>
        <title>Manage API Clients</title>

        <script>
            function deleteAccessToken(id) {
                $.getJSON("tokenManage.json",
                    {
                        method: "deleteAccessToken",
                        id: id
                    },
                    function (xml) {
                        if (xml.success)
                            window.location = 'clients.jsp';
                        else
                            alert(xml.error);
                    });
            }

            function deleteRequestToken(id) {
                $.getJSON("tokenManage.json",
                    {
                        method: "deleteRequestToken",
                        id: id
                    },
                    function (xml) {
                        if (xml.success)
                            window.location = 'clients.jsp';
                        else
                            alert(xml.error);
                    });
            }
        </script>
    </head>

    <body>
    <div class="container">

        <div class="page-header-bar">
            <h4 class="page-header-title">
                <i class="fas fa-key page-header-icon"></i>&nbsp;Manage API Client/Tokens
            </h4>
        </div>

        <h5 class="mt-3">Request Tokens</h5>
        <table class="table table-striped table-sm table-hover">
            <thead>
                <tr>
                    <th>Client Name</th>
                    <th>Date Created</th>
                    <th>Verified</th>
                    <th style="width:80px;">Actions</th>
                </tr>
            </thead>
            <tbody>
                <% if (requestTokens.size() > 0) { %>
                    <% for (ServiceRequestToken srt : requestTokens) { %>
                <tr>
                        <%
                            ServiceClient client = clientMap.get(srt.getClientId());
                            if (client != null) {
                        %>
                    <td><%=Encode.forHtml(client.getName())%></td>
                    <td><%=dateFormatter.format(srt.getDateCreated())%></td>
                    <td><%=Encode.forHtml(srt.getVerifier())%></td>
                    <td>
                        <a href="javascript:void(0);" onclick="deleteRequestToken('<%=srt.getId()%>');"
                           title="Delete" class="text-danger"><i class="fas fa-trash-alt"></i></a>
                    </td>
                        <% } else { %>
                    <td colspan="4">Client not found</td>
                        <% } %>
                </tr>
                    <% } %>
                <% } else { %>
                <tr>
                    <td colspan="4">
                        <span class="text-muted">No Request Tokens found.</span>
                    </td>
                </tr>
                <% } %>
            </tbody>
        </table>

        <h5 class="mt-4">Access Tokens</h5>
        <table class="table table-striped table-sm table-hover">
            <thead>
                <tr>
                    <th>Client Name</th>
                    <th>Date Created</th>
                    <th>Expires</th>
                    <th style="width:80px;">Actions</th>
                </tr>
            </thead>
            <tbody>
                <% if (accessTokens.size() > 0) { %>
                    <% for (ServiceAccessToken sat : accessTokens) { %>
                <tr>
                    <td><%=Encode.forHtml(clientMap.get(sat.getClientId()).getName())%></td>
                    <td><%=dateFormatter.format(sat.getDateCreated())%></td>
                    <td>
                        <%
                            Date d = new Date();
                            d.setTime(sat.getIssued() * 1000);
                            Calendar c = Calendar.getInstance();
                            c.setTime(d);
                            c.add(Calendar.SECOND, (int) sat.getLifetime());
                        %>
                        <%=dateFormatter.format(c.getTime())%>
                    </td>
                    <td>
                        <a href="javascript:void(0);" onclick="deleteAccessToken('<%=sat.getId()%>');"
                           title="Delete" class="text-danger"><i class="fas fa-trash-alt"></i></a>
                    </td>
                </tr>
                    <% } %>
                <% } else { %>
                <tr>
                    <td colspan="4">
                        <span class="text-muted">No Access Tokens found.</span>
                    </td>
                </tr>
                <% } %>
            </tbody>
        </table>

    </div>
    </body>
</html>
