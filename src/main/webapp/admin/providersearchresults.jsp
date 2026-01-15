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

--%>
<!DOCTYPE html>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>


<%@ page import="java.sql.*, java.util.*, ca.openosp.*" buffer="none" %>
<%@ page import="ca.openosp.openo.utility.SpringUtils" %>
<%@ page import="ca.openosp.openo.commn.model.ProviderData" %>
<%@ page import="ca.openosp.openo.commn.dao.ProviderDataDao" %>
<%@ page import="org.owasp.encoder.Encode" %>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_admin,_admin.userAdmin" rights="*" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_admin&type=_admin.userAdmin");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>


<html>
    <head>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.providersearchresults.title"/></title>
        <link href="${pageContext.request.contextPath}/css/DT_bootstrap.css" rel="stylesheet">
        <link href="${pageContext.request.contextPath}/library/DataTables-1.10.12/media/css/jquery.dataTables.min.css"
              rel="stylesheet">
        <link href="${pageContext.request.contextPath}/css/bootstrap.css" rel="stylesheet" type="text/css">
        <!-- Bootstrap 2.3.1 -->

        <script src="${pageContext.request.contextPath}/library/jquery/jquery-3.6.4.min.js"></script>
        <script src="${pageContext.request.contextPath}/library/DataTables/datatables.min.js"></script>
        <!-- DataTables 1.13.4 -->

        <script>
            function setfocus() {
                document.searchprovider.keyword.focus();
                document.searchprovider.keyword.select();
            }

            function onsub() {
                var keyword = document.searchprovider.keyword.value;
                var keywordLowerCase = keyword.toLowerCase();
                document.searchprovider.keyword.value = keywordLowerCase;

            }
        </script>
        <script>
            jQuery(document).ready(function () {
                jQuery('#tblResults').DataTable({
                    "language": {
                        "url": "<%=request.getContextPath() %>/library/DataTables/i18n/<fmt:setBundle basename="oscarResources"/><fmt:message key="global.i18nLanguagecode"/>.json"
                    }
                });
            });
        </script>

    </head>

    <%
        //Defaults
        String strOffset = "0";
        String strLimit = "10000";

        //OFFSET
        if (request.getParameter("limit1") != null)
            strOffset = request.getParameter("limit1");
        //LIMIT
        if (request.getParameter("limit2") != null)
            strLimit = request.getParameter("limit2");

        String keyword = Encode.forHtmlContent(request.getParameter("keyword"));
        String orderBy = request.getParameter("orderby");
        String searchMode = request.getParameter("search_mode");
        if (searchMode == null)
            searchMode = "search_name";
        if (orderBy == null)
            orderBy = "last_name";

        String searchStatus = ("All".equalsIgnoreCase(request.getParameter("search_status")) ? null : request.getParameter("search_status"));

        int offset = Integer.parseInt(strOffset);
        int limit = Integer.parseInt(strLimit);
    %>
    <body onLoad="setfocus()">

    <h4>
        <i class="icon-search" title="Patient Search"></i>&nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="admin.providersearchresults.description"/></h4>

    <form method="post" action="providersearchresults.jsp" name="searchprovider" onsubmit="return onsub()">
        <div class="well">
            <table style="width:100%">
                <tr>
                    <td rowspan="2" style="text-align:right; vertical-align:middle">
                        <b class="blue-text"><i><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.search.formSearchCriteria"/></i>
                    </td>
                    <td style="white-space: nowrap;">
                        <input type="radio" <%=searchMode.equals("search_name")?"checked":""%> name="search_mode"
                               value="search_name" onclick="document.forms['searchprovider'].keyword.focus();">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.providersearch.formLastName"/>
                    </td>
                    <td style="white-space: nowrap;">
                        <input type="radio"    <%=searchMode.equals("search_providerno")?"checked":""%>
                               name="search_mode"
                               value="search_providerno" onclick="document.forms['searchprovider'].keyword.focus();">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formProviderNo"/>
                    </td>
                    <td style="white-space: nowrap;">
                        <input type="radio" name="search_status" value="All" <%=searchStatus == null ? "checked" : ""%>>
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.providersearch.formAllStatus"/>
                        <br/>
                        <input type="radio" name="search_status"
                               value="1" <%="1".equals(searchStatus) ? "checked" : ""%>>
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.providersearch.formActiveStatus"/>
                        <br/>
                        <input type="radio" name="search_status"
                               value="0" <%="0".equals(searchStatus) ? "checked" : ""%>>
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.providersearch.formInactiveStatus"/>
                    </td>
                    <td style="vertical-align:middle; text-align:left" rowspan="2">
                        <div class="input-append">
                            <input type="text" name="keyword" class="input input-large" maxlength="100"
                                   style="height:24px">
                            <button type="submit" name="button" class="btn add-on" style="height:24px"><i
                                    class="icon-search" title="<fmt:setBundle basename="oscarResources"/><fmt:message key="admin.search.btnSubmit"/>"></i>
                            </button>
                        </div>
                        <input type="hidden" name="orderby" value="last_name">
                        <input type="hidden" name="limit1" value="0">
                        <input type="hidden" name="limit2" value="10000">

                    </td>
                </tr>
            </table>
        </div>
    </form>

    <table>
        <tr>
            <td style="text-align:left"><i><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.search.keywords"/></i>
                : <%=Encode.forHtml(keyword)%>
            </td>
        </tr>
    </table>

    <table id="tblResults" style="width:100%" class="table table-hover table-striped table-condensed">
        <thead>
        <tr>
            <th style="text-align:center; width:10%">
                <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.providersearchresults.ID"/></th>
            <th style="text-align:center; width:20%; white-space: nowrap;">
                <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formLastName"/>,&nbsp;
                <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formFirstName"/></th>
            <th style="text-align:center; width:10%"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formProviderNo"/></th>
            <th style="text-align:center; width:18%">
                <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formSpecialty"/></th>
            <th style="text-align:center; width:14%">
                <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formTeam"/></th>
            <th style="text-align:center; width:4%">
                <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formSex"/></th>
            <th style="text-align:center; width:14%">
                <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.providersearchresults.phone"/></th>
            <th style="text-align:center; width:10%">
                <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formStatus"/></th>
        </tr>
        </thead>
        <tbody>
        <%
            List<ProviderData> providerList = null;
            ProviderDataDao providerDao = SpringUtils.getBean(ProviderDataDao.class);

            if (searchMode.equals("search_name")) {
                providerList = providerDao.findByProviderName(keyword, searchStatus, limit, offset);
            } else if (searchMode.equals("search_providerno")) {
                providerList = providerDao.findByProviderNo(keyword, searchStatus, limit, offset);
            }

            if (orderBy.equals("last_name")) {
                Collections.sort(providerList, ProviderData.LastNameComparator);
            } else if (orderBy.equals("first_name")) {
                Collections.sort(providerList, ProviderData.FirstNameComparator);
            } else if (orderBy.equals("provider_no")) {
                Collections.sort(providerList, ProviderData.ProviderNoComparator);
            }

            boolean toggleLine = false;
            int nItems = 0;

            if (providerList == null) {
                out.println("failed!!!");
            } else {

                for (ProviderData provider : providerList) {
                    toggleLine = !toggleLine;
                    nItems++;
        %>
        <!-- getPractionerNo() getPractitionerNoType() getFormattedName() getComments() getBillingNo() getTitle() getEmail() getOhipNo() getAddress() -->
        <tr>
            <td style="text-align:center"><a
                    href='providerupdateprovider.jsp?keyword=<%=provider.getId()%>'><%= provider.getId() %>
            </a></td>
            <td><%= Encode.forHtmlContent(provider.getLastName() + ", " + provider.getFirstName()) %>
            </td>
            <td style="text-align:center"><%= Encode.forHtmlContent(provider.getOhipNo())%>
            </td>
            <td><%= Encode.forHtmlContent(provider.getSpecialty()) %>
            </td>
            <td><%= Encode.forHtmlContent(provider.getTeam()) %>
            </td>
            <td style="text-align:center"><%= Encode.forHtmlContent(provider.getSex()) %>
            </td>
            <td><%= Encode.forHtmlContent(provider.getPhone()) %>
            </td>
            <td><%= (provider.getStatus() != null) ? (provider.getStatus().equals("1") ? "Active" : "Inactive") : "" %>
            </td>
        </tr>
        <%
                }
            }
        %>
        </tbody>
    </table>

    <br>
    <%
        int nLastPage = 0, nNextPage = 0;

        nNextPage = Integer.parseInt(strLimit) + Integer.parseInt(strOffset);
        nLastPage = Integer.parseInt(strOffset) - Integer.parseInt(strLimit);
        String searchStatusQ = (searchStatus != null) ? "&search_status=" + searchStatus : "";
        if (nLastPage >= 0) {
    %> <a
            href="providersearchresults.jsp?keyword=<%= Encode.forUriComponent(keyword) %>&search_mode=<%= searchMode %><%= searchStatusQ %>&orderby=<%=orderBy%>&limit1=<%=nLastPage%>&limit2=<%=strLimit%>"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.providersearchresults.btnLastPage"/></a> | <%
        }
        if (nItems == Integer.parseInt(strLimit)) {
    %> <a
            href="providersearchresults.jsp?keyword=<%= Encode.forUriComponent(keyword) %>&search_mode=<%= searchMode %><%= searchStatusQ %>&orderby=<%= orderBy %>&limit1=<%=nNextPage%>&limit2=<%=strLimit%>"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.providersearchresults.btnNextPage"/></a> <%
        }
    %>
    <p><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.providersearchresults.msgClickForEditing"/></p>
    </center>
    </body>
</html>