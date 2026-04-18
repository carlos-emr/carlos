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

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_search" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_search");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>

<%@ taglib uri="/WEB-INF/caisi-tag.tld" prefix="caisi" %>
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>
<%@ taglib uri="carlos" prefix="carlos" %>

<%
    String curProvider_no = request.getParameter("provider_no");

    String strOffset = "0";
    String strLimit = "10";
    StringBuilder bufChart = null, bufName = null, bufNo = null, bufDoctorNo = null;

    String keyword = request.getParameter("keyword");
    if (request.getParameter("limit1") != null) strOffset = request.getParameter("limit1");
    if (request.getParameter("limit2") != null) strLimit = request.getParameter("limit2");

    int limit;
    try {
        limit = Integer.parseInt(strLimit);
    } catch (NumberFormatException e) {
        limit = 10;
    }
    int offset;
    try {
        offset = Integer.parseInt(strOffset);
    } catch (NumberFormatException e) {
        offset = 0;
    }
    // Sanitize: replace raw request strings with parsed integer values to prevent XSS
    strLimit = String.valueOf(limit);
    strOffset = String.valueOf(offset);

    boolean caisi = Boolean.valueOf(request.getParameter("caisi")).booleanValue();

    // Validate originalpage to prevent open redirect: must be a relative URL.
    // Note: getParameter() auto-decodes URL-encoded values, so %2F%2F decodes to // and is
    // caught by startsWith("//"). Backslash bypass (/\) is also rejected explicitly.
    String originalpage = request.getParameter("originalpage");
    if (originalpage == null || originalpage.isEmpty() || !originalpage.startsWith("/") || originalpage.startsWith("//") || originalpage.startsWith("/\\")) {
        originalpage = request.getContextPath() + "/appointment/addappointment";
    }
    // Choose ? or & depending on whether originalpage already has a query string
    String originalPageSeparator = originalpage.contains("?") ? "&" : "?";

%>

<%@ page import="java.util.*, java.sql.*,java.net.*, io.github.carlos_emr.*" %>
<%@ page import="java.nio.charset.StandardCharsets" %>

<%@page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.Demographic" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.DemographicDao" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ page import="io.github.carlos_emr.carlos.util.StringUtils" %>
<%@ page import="io.github.carlos_emr.Misc" %>

<jsp:useBean id="providerBean" class="java.util.Properties" scope="session"/>

<%
    List<Demographic> demoList = null;
    DemographicDao demographicDao = (DemographicDao) SpringUtils.getBean(DemographicDao.class);

    String[] statusArray = {"IN", "DE", "IC", "ID", "MO", "FI"};
    List<String> statusList = Arrays.asList(statusArray);
    String statusString = "'IN','DE','IC','ID','MO','FI'";

%>

<html>
<head>
    <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
    <title><fmt:message key="demographic.demographicsearch2apptresults.title"/>(demographicsearch2reportresults)</title>

    <link rel="stylesheet" type="text/css" media="all" href="<%= request.getContextPath() %>/share/css/extractedFromPages.css"/>
    <script language="JavaScript">
        function setfocus() {
            this.focus();
            document.titlesearch.keyword.focus();
            document.titlesearch.keyword.select();
        }

        function checkTypeIn() {
            var dob = document.titlesearch.keyword;

            if (dob.value.indexOf('%b610054') == 0 && dob.value.length > 18) {
                document.titlesearch.keyword.value = dob.value.substring(8, 18);
                document.titlesearch.search_mode[4].checked = true;
            }

            if (document.titlesearch.search_mode[2].checked) {
                if (dob.value.length == 8) {
                    dob.value = dob.value.substring(0, 4) + "-" + dob.value.substring(4, 6) + "-" + dob.value.substring(6, 8);
                }
                if (dob.value.length != 10) {
                    alert("<fmt:message key="demographic.demographicsearch2apptresults.msgWrongDOB"/>");
                    return false;
                } else {
                    return true;
                }
            } else {
                return true;
            }
        }

        function searchInactive() {
            document.titlesearch.ptstatus.value = "inactive"
            if (checkTypeIn()) document.forms[0].submit()
        }

        function searchAll() {
            document.titlesearch.ptstatus.value = ""
            if (checkTypeIn()) document.forms[0].submit()
        }


    </SCRIPT>
</head>
<body bgcolor="white" bgproperties="fixed" onLoad="setfocus()" topmargin="0" leftmargin="0" rightmargin="0">

<table border="0" cellspacing="0" cellpadding="0" width="100%">
    <tr class="subject">
        <th><fmt:message key="demographic.demographicsearch2apptresults.patientsRecord"/></th>
    </tr>
</table>
<table border="0" cellpadding="1" cellspacing="0" width="100%"
       bgcolor="#CCCCFF">
</table>

<table width="95%" border="0">
    <tr>
        <td align="left"><fmt:message key="demographic.demographicsearch2apptresults.msgKeywords"/> <carlos:encode value='<%= StringUtils.noNull(request.getParameter("keyword")) %>' context="html"/>
        </td>
    </tr>
</table>

<script language="JavaScript">

    var fullname = "";
    <%-- RJ 07/10/2006 Need to pass doctor of patient back to referrer --%>

    function addName(demographic_no, lastname, firstname, chartno, messageID, doctorNo) {
        fullname = lastname + "," + firstname;
        document.addform.action = "<carlos:encode value='<%= originalpage %>' context="javaScript"/><%= originalPageSeparator %>demographicNoParam=" + demographic_no + "&demographic_no=" + demographic_no + "&firstNameParam=" + firstname + "&lastNameParam=" + lastname + "&chart_no=" + chartno;
        document.addform.submit();
        return true;
    }

    <%if(caisi) {%>

    function addNameCaisi(demographic_no, lastname, firstname, chartno, messageID) {
        fullname = lastname + "," + firstname;
        if (opener.document['<carlos:encode value='<%= StringUtils.noNull(request.getParameter("formName")) %>' context="javaScriptBlock"/>'] != null) {
            if (opener.document['<carlos:encode value='<%= StringUtils.noNull(request.getParameter("formName")) %>' context="javaScriptBlock"/>'].
            elements['<carlos:encode value='<%= StringUtils.noNull(request.getParameter("elementName")) %>' context="javaScriptBlock"/>'] != null
        )
            opener.document
        ['<carlos:encode value='<%= StringUtils.noNull(request.getParameter("formName")) %>' context="javaScriptBlock"/>'].
            elements['<carlos:encode value='<%= StringUtils.noNull(request.getParameter("elementName")) %>' context="javaScriptBlock"/>'].value = fullname;
            if (opener.document['<carlos:encode value='<%= StringUtils.noNull(request.getParameter("formName")) %>' context="javaScriptBlock"/>'].
            elements['<carlos:encode value='<%= StringUtils.noNull(request.getParameter("elementId")) %>' context="javaScriptBlock"/>'] != null
        )
            opener.document
        ['<carlos:encode value='<%= StringUtils.noNull(request.getParameter("formName")) %>' context="javaScriptBlock"/>'].
            elements['<carlos:encode value='<%= StringUtils.noNull(request.getParameter("elementId")) %>' context="javaScriptBlock"/>'].value = demographic_no;
        }
        self.close();
    }

    <%}%>
</SCRIPT>

<CENTER>
    <table width="100%" border="0" cellpadding="0" cellspacing="1"
           bgcolor="#C0C0C0">
        <form method="post" name="addform"
              action="<%= request.getContextPath() %>/appointment/addappointment">
            <tr class="title">
                <TH width="20%"><b><fmt:message key="demographic.demographicsearch2apptresults.demographicId"/></b></TH>
                <TH width="20%"><b><fmt:message key="demographic.demographicsearch2apptresults.lastName"/></b></TH>
                <TH width="20%"><b><fmt:message key="demographic.demographicsearch2apptresults.firstName"/></b></TH>
                <TH width="5%"><b><fmt:message key="demographic.demographicsearch2apptresults.age"/></b></TH>
                <TH width="10%"><b><fmt:message key="demographic.demographicsearch2apptresults.rosterStatus"/></b></TH>
                <TH width="5%"><b><fmt:message key="demographic.demographicsearch2apptresults.sex"/></B></TH>
                <TH width="10%"><b><fmt:message key="demographic.demographicsearch2apptresults.DOB"/></B></TH>
                <TH width="10%"><b><fmt:message key="demographic.demographicsearch2apptresults.doctor"/></B></TH>
            </tr>

            <%

                boolean toggleLine = false;
                int nItems = 0;

                demoList = demographicDao.searchDemographicByNameAndNotStatus(keyword, statusList, limit, offset, curProvider_no, true);
                int dSize = demoList.size();


                if (demoList == null) {
                    out.println("failed!!!");
                } else {

                    for (Demographic demo : demoList) {

                        toggleLine = !toggleLine;
                        nItems++; //to calculate if it is the end of records


                        String bgColor = toggleLine ? "#EEEEFF" : "white";
            %>
            <c:set var="__enc_1"><carlos:encode value='<%= StringUtils.noNull(demo.getLastName()) %>' context="uriComponent"/></c:set>
            <c:set var="__enc_2"><carlos:encode value='<%= StringUtils.noNull(demo.getFirstName()) %>' context="uriComponent"/></c:set>
            <c:set var="__enc_3"><carlos:encode value='<%= StringUtils.noNull(demo.getChartNo()) %>' context="uriComponent"/></c:set>

            <tr bgcolor="<%=bgColor%>" align="center"
                onMouseOver="this.style.cursor='hand';this.style.backgroundColor='pink';"
                onMouseout="this.style.backgroundColor='<%=bgColor%>';"
                onClick="<% if(caisi) { out.print("addNameCaisi");}
						else { out.print("addName");} %>('<%=demo.getDemographicNo()%>','<carlos:encode value='${__enc_1}' context="javaScriptAttribute"/>','<carlos:encode value='${__enc_2}' context="javaScriptAttribute"/>','<carlos:encode value='${__enc_3}' context="javaScriptAttribute"/>','<carlos:encode value='<%= StringUtils.noNull(request.getParameter("messageId")) %>' context="javaScriptAttribute"/>','<carlos:encode value='<%= StringUtils.noNull(demo.getProviderNo()) %>' context="javaScriptAttribute"/>')">

                <c:set var="__enc_4"><carlos:encode value='<%= StringUtils.noNull(demo.getLastName()) %>' context="uriComponent"/></c:set>
                <c:set var="__enc_5"><carlos:encode value='<%= StringUtils.noNull(demo.getFirstName()) %>' context="uriComponent"/></c:set>
                <c:set var="__enc_6"><carlos:encode value='<%= StringUtils.noNull(demo.getChartNo()) %>' context="uriComponent"/></c:set>
                <td><input type="submit" class="mbttn" name="demographic_no" value="<%=demo.getDemographicNo()%>"
                           onClick="<% if(caisi) {out.print("addNameCaisi");}
					else { out.print("addName");} %>('<%=demo.getDemographicNo()%>','<carlos:encode value='${__enc_4}' context="javaScriptAttribute"/>','<carlos:encode value='${__enc_5}' context="javaScriptAttribute"/>','<carlos:encode value='${__enc_6}' context="javaScriptAttribute"/>','<carlos:encode value='<%= StringUtils.noNull(request.getParameter("messageId")) %>' context="javaScriptAttribute"/>','<carlos:encode value='<%= StringUtils.noNull(demo.getProviderNo()) %>' context="javaScriptAttribute"/>')">
                </td>
                <td><carlos:encode value='<%= Misc.toUpperLowerCase(demo.getLastName()) %>' context="html"/>
                </td>
                <td><carlos:encode value='<%= Misc.toUpperLowerCase(demo.getFirstName()) %>' context="html"/>
                </td>
                <td><carlos:encode value='<%= demo.getAge() == null ? "" : String.valueOf(demo.getAge()) %>' context="html"/>
                </td>
                <td><carlos:encode value='<%= demo.getRosterStatus() == null ? "" : demo.getRosterStatus() %>' context="html"/>
                </td>
                <td><carlos:encode value='<%= demo.getSex() == null ? "" : demo.getSex() %>' context="html"/>
                </td>
                <td><carlos:encode value='<%= demo.getFormattedDob() == null ? "" : demo.getFormattedDob() %>' context="html"/>
                </td>
                <td><carlos:encode value='<%= providerBean.getProperty(demo.getProviderNo()) == null ? "" : providerBean.getProperty(demo.getProviderNo()) %>' context="html"/>
                </td>
            </tr>
            <%
                        bufName = new StringBuilder(demo.getLastName() + "," + demo.getFirstName());
                        bufNo = new StringBuilder(demo.getDemographicNo());
                        bufChart = new StringBuilder(demo.getChartNo());
                        bufDoctorNo = new StringBuilder(demo.getProviderNo());
                    }
                }
            %>
            <%
                String temp = null;
                for (Enumeration e = request.getParameterNames(); e.hasMoreElements(); ) {
                    temp = e.nextElement().toString();
                    if (temp.equals("keyword") || temp.equals("dboperation") || temp.equals("displaymode") || temp.equals("submit") || temp.equals("chart_no"))
                        continue;
                    out.println("<input type='hidden' name='" + Encode.forHtmlAttribute(temp) + "' value='" + Encode.forHtmlAttribute(StringUtils.noNull(request.getParameter(temp))) + "'>");
                }

                //should close the pipe connected to the database here!!!
            %>
        </form>

    </table>
    <%
        int nLastPage = 0, nNextPage = 0;
        nNextPage = Integer.parseInt(strLimit) + Integer.parseInt(strOffset);
        nLastPage = Integer.parseInt(strOffset) - Integer.parseInt(strLimit);
    %>

    <%
        if (nItems == 0 && nLastPage <= 0) {
    %> <caisi:isModuleLoad moduleName="caisi" reverse="true">
    <fmt:message key="demographic.search.noResultsWereFound"/>
    <a href="<%= request.getContextPath() %>/demographic/ViewDemographicAddARecordHtm?search_mode=<carlos:encode value='<%= StringUtils.noNull(request.getParameter("search_mode")) %>' context="uriComponent"/>&keyword=<carlos:encode value='<%= StringUtils.noNull(request.getParameter("keyword")) %>' context="uriComponent"/>"><fmt:message key="demographic.search.btnCreateNew"/></a>
</caisi:isModuleLoad> <%
    }
%>
    <script language="JavaScript">
        <!--
        function last() {
            <c:set var="__enc_7"><carlos:encode value='<%= originalpage %>' context="uriComponent"/></c:set>
            <c:set var="__enc_8"><carlos:encode value='<%= StringUtils.noNull(request.getParameter("keyword")) %>' context="uriComponent"/></c:set>
            <c:set var="__enc_9"><carlos:encode value='<%= StringUtils.noNull(request.getParameter("search_mode")) %>' context="uriComponent"/></c:set>
            <c:set var="__enc_10"><carlos:encode value='<%= StringUtils.noNull(request.getParameter("orderby")) %>' context="uriComponent"/></c:set>
            document.nextform.action = "<%= request.getContextPath() %>/demographic/ViewDemographicSearch2ReportResults?originalpage=<carlos:encode value='${__enc_7}' context="javaScript"/>&keyword=<carlos:encode value='${__enc_8}' context="javaScript"/>&search_mode=<carlos:encode value='${__enc_9}' context="javaScript"/>&orderby=<carlos:encode value='${__enc_10}' context="javaScript"/>&limit1=<%=nLastPage%>&limit2=<%=strLimit%>";
            //document.nextform.submit();
        }

        function next() {
            <c:set var="__enc_11"><carlos:encode value='<%= originalpage %>' context="uriComponent"/></c:set>
            <c:set var="__enc_12"><carlos:encode value='<%= StringUtils.noNull(request.getParameter("keyword")) %>' context="uriComponent"/></c:set>
            <c:set var="__enc_13"><carlos:encode value='<%= StringUtils.noNull(request.getParameter("search_mode")) %>' context="uriComponent"/></c:set>
            <c:set var="__enc_14"><carlos:encode value='<%= StringUtils.noNull(request.getParameter("orderby")) %>' context="uriComponent"/></c:set>
            document.nextform.action = "<%= request.getContextPath() %>/demographic/ViewDemographicSearch2ReportResults?originalpage=<carlos:encode value='${__enc_11}' context="javaScript"/>&keyword=<carlos:encode value='${__enc_12}' context="javaScript"/>&search_mode=<carlos:encode value='${__enc_13}' context="javaScript"/>&orderby=<carlos:encode value='${__enc_14}' context="javaScript"/>&limit1=<%=nNextPage%>&limit2=<%=strLimit%>";
            //document.nextform.submit();
        }

        //-->
    </SCRIPT>

    <form method="post" name="nextform" action="<%= request.getContextPath() %>/demographic/ViewDemographicSearch2ReportResults">
        <%
            if (nLastPage >= 0) {
        %> <input type="submit" class="mbttn" name="submit"
                  value="<fmt:message key="demographic.demographicsearch2apptresults.btnPrevPage"/>"
                  onClick="last()"> <%
        }
        if (nItems == Integer.parseInt(strLimit)) {
    %> <input type="submit" class="mbttn" name="submit"
              value="<fmt:message key="demographic.demographicsearch2apptresults.btnNextPage"/>"
              onClick="next()"> <%
        }
    %> <%
        for (Enumeration e = request.getParameterNames(); e.hasMoreElements(); ) {
            temp = e.nextElement().toString();
            if (temp.equals("submit") || temp.equals("chart_no")) continue;
            out.println("<input type='hidden' name='" + Encode.forHtmlAttribute(temp) + "' value='" + Encode.forHtmlAttribute(StringUtils.noNull(request.getParameter(temp))) + "'>");

        }
    %>
    </form>
</CENTER>
<a href="#" onClick="history.go(-1);return false;">
    <img src="<%= request.getContextPath() %>/images/leftarrow.gif" border="0" width="25" height="20" align="absmiddle"> Back
</a>
</body>
</html>
