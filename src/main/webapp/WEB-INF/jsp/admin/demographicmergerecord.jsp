<!DOCTYPE html>
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
<%@ page import="io.github.carlos_emr.carlos.demographic.data.DemographicMergeSearch" %>
<%@ page import="io.github.carlos_emr.carlos.util.StringUtils" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="https://owasp.org/www-project-csrfguard/Owasp.CsrfGuard.tld" prefix="csrf" %>
<%@ taglib uri="/WEB-INF/caisi-tag.tld" prefix="caisi" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setLocale value="<%= io.github.carlos_emr.carlos.utility.LocaleUtils.resolveBundleLocale(request) %>"/>
<fmt:setBundle basename="oscarResources"/>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_demographic" rights="w" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_demographic");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%

    DemographicMergeSearch mergeSearch = (DemographicMergeSearch) request.getAttribute("mergeSearch");
    int offset = mergeSearch.offset();
    int limit = mergeSearch.limit();
    String strOffset = Integer.toString(offset);
    String strLimit = Integer.toString(limit);
    String outcome = request.getParameter("outcome");
    boolean mergedSearch = mergeSearch.merged();
    if (outcome != null) {
        if (outcome.equals("success")) {
%>
<script language="JavaScript">
    alert("<fmt:message key='admin.demographicmergerecord.msgMergeSuccess'/>");
</script>
<%
} else if (outcome.equals("failure")) {
%>
<script language="JavaScript">
    alert("<fmt:message key='admin.demographicmergerecord.msgMergeFailed'/>");
</script>
<%
} else if (outcome.equals("successUnMerge")) {
%>
<script language="JavaScript">
    alert("<fmt:message key='admin.demographicmergerecord.msgUnmergeSuccess'/>");
</script>
<%
} else if (outcome.equals("failureUnMerge")) {
%>
<script language="JavaScript">
    alert("<fmt:message key='admin.demographicmergerecord.msgUnmergeFailed'/>");
</script>
<%
        }
    }
%>

<%@ page import="java.util.*, java.sql.*, io.github.carlos_emr.*, io.github.carlos_emr.carlos.demographic.data.DemographicMerged" %>
<%@ page import="java.lang.System" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Demographic" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SafeEncode" %>

<%
    List<Demographic> demoList = (List<Demographic>) request.getAttribute("mergeSearchResults");
    String keyword = mergeSearch.keyword();
    String orderBy = mergeSearch.orderBy();
    String searchMode = mergeSearch.mode();

%>

<html>
<head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
    <title><fmt:message key="admin.admin.mergeRec"/></title>
    <link href="<%=request.getContextPath() %>/library/bootstrap/5.3.8/css/bootstrap.min.css" rel="stylesheet">
    <script src="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/share/javascript/dobSearchKeyword.js"></script>
    <fmt:message key="demographic.zdemographicfulltitlesearch.msgDobFormat" var="dobFormatMessage"/>
    <script language="JavaScript">
        var DOB_FORMAT_MESSAGE = '${carlos:forJavaScript(dobFormatMessage)}';
        function setfocus() {
            document.titlesearch.keyword.focus();
            document.titlesearch.keyword.select();
        }

        function checkTypeIn() {
            var form = document.titlesearch;
            var keyword = form.keyword;
            // Preserve Ontario card-swiping support when selecting a patient.
            if (/^%b610054[0-9]{10}/.test(keyword.value)) {
                keyword.value = keyword.value.substring(8, 18);
                form.search_mode.value = 'search_hin';
                return true;
            }
            if (form.search_mode.value === 'search_dob') {
                var value = keyword.value.trim();
                // Server-populated/restored values need not fire an input event.
                if (/^[0-9]{8}$/.test(value)) {
                    keyword.value = CarlosDobSearch.format(value);
                    value = keyword.value;
                }
                if (value.length > 0 && !CarlosDobSearch.isValid(value)) {
                    alert(DOB_FORMAT_MESSAGE);
                    return false;
                }
            }
            return true;
        }

        function confirmMerge() {
            const message = "<fmt:message key='admin.demographicmergerecord.confirmMerge'/>";
            const userConfirmed = confirm(message);
            if (!userConfirmed) {
                return false;
            }
            return checkTypeIn();
        }

        function UnMerge() {
            document.mergeform.mergeAction.value = "unmerge";
        }

        function popupWindow(page) {
            windowprops = "height=660, width=960, location=no, scrollbars=yes, menubars=no, toolbars=no, resizable=yes, top=0, left=0";
            var popup = window.open(page, "labreport", windowprops);
            popup.focus();
        }
    </SCRIPT>
    <!--base target="pt_srch_main"-->

    <style>
        input[type="radio"] {
            margin-left: 8px;
        }
    </style>


</head>
<body onLoad="setfocus()">
<div class="container-fluid card card-body bg-body-tertiary">
    <h3><fmt:message key="admin.admin.mergeRec"/></h3>

    <form method="post" name="titlesearch" action="${pageContext.request.contextPath}/admin/DemographicMergeRecord" class="d-flex flex-wrap align-items-center gap-2"
          onSubmit="return checkTypeIn()">
        <input type="hidden" name="<csrf:tokenname/>" value="<csrf:tokenvalue/>"/>

        <fmt:message key="admin.demographicmergerecord.searchPrompt"/>

        <input type="radio" name="search_mode" value="search_name" <%=searchMode.equals("search_name")?"checked":""%> >
        <fmt:message key="admin.demographicmergerecord.name"/>
        <input type="radio" name="search_mode"
               value="search_phone" <%=searchMode.equals("search_phone")?"checked":""%>    > <fmt:message key="admin.demographicmergerecord.phone"/>
        <input type="radio" name="search_mode" value="search_dob" <%=searchMode.equals("search_dob")?"checked":""%> >
        <fmt:message key="admin.demographicmergerecord.dob"/>
        <input type="radio" name="search_mode"
               value="search_address" <%=searchMode.equals("search_address")?"checked":""%>> <fmt:message key="admin.demographicmergerecord.address"/>
        <input type="radio" name="search_mode" value="search_hin" <%=searchMode.equals("search_hin")?"checked":""%>> <fmt:message key="admin.demographicmergerecord.hin"/>

        <input type="text" NAME="keyword" class="form-control"
               oninput="if(document.titlesearch.search_mode.value === 'search_dob') CarlosDobSearch.formatInput(this);" MAXLENGTH="100" value="<%=(keyword != null)?SafeEncode.forHtmlAttribute(keyword):""%>">
        <INPUT TYPE="hidden" NAME="orderby" VALUE="last_name">
        <INPUT TYPE="hidden" NAME="limit1" VALUE="0">
        <INPUT TYPE="hidden" NAME="limit2" VALUE="10">

        <INPUT class="btn btn-secondary" TYPE="SUBMIT" NAME="button" VALUE="<fmt:message key='Search'/>">
        <button class="btn btn-secondary" type="submit" name="dboperation" value="demographic_search_merged"><fmt:message key="admin.demographicmergerecord.searchMergedRecords"/></button>
    </form>
</div><!--well-->

<% if (request.getParameter("keyword") != null) {%>

        <i><fmt:message key="admin.demographicmergerecord.resultsBasedOnKeywords"/></i> : <carlos:encode value='<%= StringUtils.noNull(request.getParameter("keyword")) %>' context="html"/>

<form id="search-sort" method="post" action="${pageContext.request.contextPath}/admin/DemographicMergeRecord">
    <input type="hidden" name="<csrf:tokenname/>" value="<csrf:tokenvalue/>"/>
    <c:forTokens var="searchField" items="keyword,dboperation,outofdomain" delims=",">
        <input type="hidden" name="${carlos:forHtmlAttribute(searchField)}" value="${carlos:forHtmlAttribute(param[searchField])}"/>
    </c:forTokens>
    <input type="hidden" name="search_mode" value="<%=SafeEncode.forHtmlAttribute(searchMode)%>"/>
    <input type="hidden" name="limit2" value="<%=limit%>"/>
    <input type="hidden" name="limit1" value="0"/>
</form>
<form id="search-page" method="post" action="${pageContext.request.contextPath}/admin/DemographicMergeRecord">
    <input type="hidden" name="<csrf:tokenname/>" value="<csrf:tokenvalue/>"/>
    <c:forTokens var="searchField" items="keyword,dboperation,outofdomain" delims=",">
        <input type="hidden" name="${carlos:forHtmlAttribute(searchField)}" value="${carlos:forHtmlAttribute(param[searchField])}"/>
    </c:forTokens>
    <input type="hidden" name="search_mode" value="<%=SafeEncode.forHtmlAttribute(searchMode)%>"/>
    <input type="hidden" name="limit2" value="<%=limit%>"/>
    <input type="hidden" name="orderby" value="<%=SafeEncode.forHtmlAttribute(orderBy)%>"/>
</form>
<CENTER>
    <form method="post" name="mergeform" action="MergeRecords" onSubmit="return confirmMerge()">
        <input type="hidden" name="mergeAction" value="merge"/>
        <input type="hidden" name="provider_no" value="<carlos:encode value='<%= session.getAttribute("user") != null ? (String)session.getAttribute("user") : "" %>' context="htmlAttribute"/>"/>

        <table class="table table-striped  table-sm">
            <tr>
                <TH align="CENTER" width="5%"></th>
                <% if (!mergedSearch) {%>
                <th align="center" width="5%"><fmt:message key="admin.demographicmergerecord.mainRecord"/></th>
                <%}%>
                <TH align="center" width="10%"><b><button type="submit" class="btn btn-link p-0" form="search-sort" name="orderby" value="demographic_no"><fmt:message key="admin.demographicmergerecord.demographic"/></button></b></font><%-- nosemgrep: java.jsp.jsp-scriptlet-xss.jsp-scriptlet-xss --%>
                </TH>
                <TH align="center" width="20%"><b><button type="submit" class="btn btn-link p-0" form="search-sort" name="orderby" value="last_name"><fmt:message key="admin.demographicmergerecord.lastName"/></button> </b></font></TH><%-- nosemgrep: java.jsp.jsp-scriptlet-xss.jsp-scriptlet-xss --%>
                <TH align="center" width="20%"><b><button type="submit" class="btn btn-link p-0" form="search-sort" name="orderby" value="first_name"><fmt:message key="admin.demographicmergerecord.firstName"/></button> </b></font></TH><%-- nosemgrep: java.jsp.jsp-scriptlet-xss.jsp-scriptlet-xss --%>
                <TH align="center" width="10%"><b><button type="submit" class="btn btn-link p-0" form="search-sort" name="orderby" value="age"><fmt:message key="admin.demographicmergerecord.age"/></button></b></font><%-- nosemgrep: java.jsp.jsp-scriptlet-xss.jsp-scriptlet-xss --%>
                </TH>
                <TH align="center" width="10%"><b><button type="submit" class="btn btn-link p-0" form="search-sort" name="orderby" value="roster_status"><fmt:message key="admin.demographicmergerecord.rosterStatus"/></button></b></font></TH><%-- nosemgrep: java.jsp.jsp-scriptlet-xss.jsp-scriptlet-xss --%>
                <TH align="center" width="10%"><b><button type="submit" class="btn btn-link p-0" form="search-sort" name="orderby" value="sex"><fmt:message key="admin.demographicmergerecord.sex"/></button></B></font><%-- nosemgrep: java.jsp.jsp-scriptlet-xss.jsp-scriptlet-xss --%>
                </TH>
                <TH align="center" width="10%"><b><button type="submit" class="btn btn-link p-0" form="search-sort" name="orderby" value="date_of_birth"><fmt:message key="admin.demographicmergerecord.dobFormat"/></button></B></Font><%-- nosemgrep: java.jsp.jsp-scriptlet-xss.jsp-scriptlet-xss --%>
                </TH>
            </tr>
            <%

                boolean toggleLine = false;
                int nItems = 0;

                    for (Demographic demo : demoList) {

                        toggleLine = !toggleLine;
                        nItems++; //to calculate if it is the end of records
            %>
            <tr>
                <%
                    DemographicMerged dmDAO = new DemographicMerged();
                    String demographicNo = demo.getDemographicNo().toString();
                    String head = dmDAO.getHead(demographicNo);

                    // default to head record
                    boolean isHeadRecord = true;

                    // if record has a head record, then it is not the head record
                    if (demo.getHeadRecord() != null)
                        isHeadRecord = false;

                    if (mergedSearch || isHeadRecord) {%>
                <td align="center" width="5%" height="25"><input type="checkbox" name="records"
                                                                 value="<carlos:encode value='<%= demographicNo %>' context="htmlAttribute"/>"></td>
                <%} else {%>
                <td align="center" width="5%" height="25">&nbsp;</td>
                <%
                    }
                    if (!mergedSearch) {
                        if (isHeadRecord) {
                %>
                <td align="center" width="5%" height="25"><input type="radio" name="head" value="<carlos:encode value='<%= demographicNo %>' context="htmlAttribute"/>">
                </td>
                <%} else {%>
                <td align="center" width="5%" height="25">&nbsp;</td>
                <%
                        }
                    }
                %>
                <td width="15%" align="center" height="25">
                    <caisi:isModuleLoad moduleName="TORONTO_RFQ" reverse="true">
                        <a href="javascript:popupWindow('<%= request.getContextPath() %>/demographic/DemographicEdit?demographic_no=<carlos:encode value='<%= head != null ? head : "" %>' context="uriComponent"/>')"><carlos:encode value='<%= demographicNo %>' context="html"/>
                        </a>
                    </caisi:isModuleLoad></td>
                <td align="center" width="20%" height="25"><carlos:encode value='<%= demo.getLastName() %>' context="html"/>
                </td>
                <td align="center" width="20%" height="25"><carlos:encode value='<%= demo.getFirstName() %>' context="html"/>
                </td>
                <td align="center" width="10%" height="25"><carlos:encode value='<%= demo.getAge() %>' context="html"/>
                </td>
                <td align="center" width="10%" height="25"><carlos:encode value='<%= demo.getRosterStatus() %>' context="html"/>
                </td>
                <td align="center" width="10%" height="25"><carlos:encode value='<%= demo.getSex() %>' context="html"/>
                </td>
                <td align="center" width="10%" height="25"><carlos:encode value='<%= demo.getFormattedDob() %>' context="html"/>
                </td>
            </tr>
            <%
                    }
            %>

        </table>

        <br>
        <% if (mergedSearch) {%>

        <input type="submit" class="btn btn-warning btn-lg" value="<fmt:message key='admin.demographicmergerecord.btnUnmergeSelected'/>" onclick="UnMerge()"/>

        <%} else {%>

        <input type="submit" class="btn btn-primary btn-lg" value="<fmt:message key='admin.demographicmergerecord.btnMergeSelected'/>"/>

        <%}%> <br/>

    </form>
    <%
        int nLastPage = 0, nNextPage = 0;
        nNextPage = Integer.parseInt(strLimit) + Integer.parseInt(strOffset);
        nLastPage = Integer.parseInt(strOffset) - Integer.parseInt(strLimit);
        if (nLastPage >= 0) {
    %> <button type="submit" class="btn btn-link p-0" form="search-page" name="limit1" value="<%=nLastPage%>"><fmt:message key="admin.demographicmergerecord.lastPage"/></button> | <%-- nosemgrep: java.jsp.jsp-scriptlet-xss.jsp-scriptlet-xss --%><%
    }
    if (nItems == Integer.parseInt(strLimit)) {
%> <button type="submit" class="btn btn-link p-0" form="search-page" name="limit1" value="<%=nNextPage%>"><%-- nosemgrep: java.jsp.jsp-scriptlet-xss.jsp-scriptlet-xss --%>
    <fmt:message key="admin.demographicmergerecord.nextPage"/></button> <%
    }


} else {// end if (request.getParameter("keyword") != null)
%>
</center>

<h3 align="center"><fmt:message key="admin.demographicmergerecord.pleaseSearch"/></h3>
<% } %>


</body>
</html>
