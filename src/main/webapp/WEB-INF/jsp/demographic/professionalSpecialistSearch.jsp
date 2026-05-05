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

<!--
/*
*
* This software is published under the GPL GNU General Public License.
* This program is free software; you can redistribute it and/or
* modify it under the terms of the GNU General Public License
* as published by the Free Software Foundation; either version 2
* of the License, or (at your option) any later version. *
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU General Public License for more details.
* You should have received a copy of the GNU General Public License
* along with this program; if not, write to the Free Software
* Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA. *
*
* <OSCAR TEAM>
*/
-->
<%@ page import="java.util.*,java.sql.*, java.net.*" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ page import="io.github.carlos_emr.carlos.util.StringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.commn.web.Contact2Action" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist" %>
<%@ page import="org.apache.commons.text.WordUtils" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SafeEncode" %>
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>
<%@ taglib uri="carlos" prefix="carlos" %>

<%@ include file="/taglibs.jsp" %>
<fmt:setBundle basename="oscarResources"/>

<%
    if (session.getAttribute("user") == null) {
        response.sendRedirect(request.getContextPath() + "/logoutPage");
    }
    String strLimit1 = "0";
    String strLimit2 = "10";
    if (request.getParameter("limit1") != null) strLimit1 = request.getParameter("limit1");
    if (request.getParameter("limit2") != null) strLimit2 = request.getParameter("limit2");

    int nItems = 0;
    Properties prop = null;
    String form = request.getParameter("form") == null ? "" : request.getParameter("form");
    String elementName = request.getParameter("elementName") == null ? "" : request.getParameter("elementName");
    String elementId = request.getParameter("elementId") == null ? "" : request.getParameter("elementId");
    String keyword = request.getParameter("keyword");

    if (request.getParameter("submit") != null
            && (request.getParameter("submit").equals("Search")
            || request.getParameter("submit").equals("Next Page")
            || request.getParameter("submit").equals("Last Page"))) {

        String search_mode = request.getParameter("search_mode") == null ? "search_name" : request.getParameter("search_mode");
        String orderBy = request.getParameter("orderby") == null ? "c.lastName,c.firstName" : request.getParameter("orderby");

        List<ProfessionalSpecialist> contacts = Contact2Action.searchProfessionalSpecialists(keyword);
        nItems = contacts.size();
        pageContext.setAttribute("contacts", contacts);
    }


%>

<html>
    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title><fmt:message key="demographic.contactSearch.titleProfessional"/></title>
        <link rel="stylesheet" type="text/css" media="all" href="<%= request.getContextPath() %>/share/css/extractedFromPages.css"/>
        <script language="JavaScript">

            <!--
            function setfocus() {
                this.focus();
                document.forms[0].keyword.focus();
                document.forms[0].keyword.select();
            }

            function check() {
                document.forms[0].submit.value = "Search";
                return true;
            }

            function selectResult(data1, data2) {
                opener.document
            ['<carlos:encode value='<%= form %>' context="javaScriptBlock"/>'].
                elements['<carlos:encode value='<%= elementId %>' context="javaScriptBlock"/>'].value = data1;
                opener.document
            ['<carlos:encode value='<%= form %>' context="javaScriptBlock"/>'].
                elements['<carlos:encode value='<%= elementName %>' context="javaScriptBlock"/>'].value = data2;
                self.close();
            }

            -->

        </script>
    </head>
    <body bgcolor="white" onload="setfocus()" topmargin="0" leftmargin="0" rightmargin="0">

    <form method="post" name="titlesearch" action="<%= request.getContextPath() %>/demographic/ViewProfessionalSpecialistSearch" onSubmit="return check();">
        <table border="0" cellpadding="1" cellspacing="0" width="100%" bgcolor="#CCCCFF">
            <tr>
                <td class="searchTitle" colspan="4"><fmt:message key="demographic.contactSearch.titleProfessional"/></td>
            </tr>
            <tr>
                <td class="blueText" width="10%" nowrap>
                    <input type="radio" name="search_mode" value="search_name" checked="checked"> <fmt:message key="demographic.contactSearch.searchByName"/>
                </td>
                <td valign="middle" rowspan="2" align="left">
                    <input type="text" name="keyword" value="" size="17" maxlength="100">
                    <input type="hidden" name="orderby" value="c.lastName, c.firstName">
                    <input type="hidden" name="limit1" value="0">
                    <input type="hidden" name="limit2" value="10">
                    <input type="hidden" name="submit" value='Search'>
                    <input type="submit" value='<fmt:message key="demographic.contactSearch.search"/>'>
                </td>
            </tr>
        </table>
        <table width="95%" border="0">
            <tr>
                <td align="left"><fmt:message key="demographic.contactSearch.resultsBasedOnKeywords"/> <carlos:encode value='<%= keyword == null ? "" : keyword %>' context="html"/>
                </td>
            </tr>
        </table>
        <input type='hidden' name='form' value="<carlos:encode value='<%= form %>' context="htmlAttribute"/>"/>
        <input type='hidden' name='elementName' value="<carlos:encode value='<%= elementName %>' context="htmlAttribute"/>"/>
        <input type='hidden' name='elementId' value="<carlos:encode value='<%= elementId %>' context="htmlAttribute"/>"/>
    </form>

    <center>
        <table width="100%" border="0" cellpadding="0" cellspacing="2"
               bgcolor="#C0C0C0">
            <tr class="title">
                <th width="25%"><b><fmt:message key="demographic.contactSearch.lastName"/></b></th>
                <th width="20%"><b><fmt:message key="demographic.contactSearch.firstName"/></b></th>
                <th width="20%"><b><fmt:message key="demographic.contactSearch.phone"/></b></th>
            </tr>

            <c:forEach var="contact" items="${contacts}" varStatus="i">
                <%
                    ProfessionalSpecialist contact = (ProfessionalSpecialist) pageContext.getAttribute("contact");
                    jakarta.servlet.jsp.jstl.core.LoopTagStatus i = (jakarta.servlet.jsp.jstl.core.LoopTagStatus) pageContext.getAttribute("i");
                    String bgColor = i.getIndex() % 2 == 0 ? "#EEEEFF" : "ivory";

                    String strOnClick;
                    strOnClick = "selectResult('" + contact.getId() + "','" + SafeEncode.forJavaScript(contact.getLastName() + "," + contact.getFirstName()) + "')";

                %>
                <tr align="center" bgcolor="<%=bgColor%>" align="center"
                    onMouseOver="this.style.cursor='hand';this.style.backgroundColor='pink';"
                    onMouseout="this.style.backgroundColor='<%=bgColor%>';" onClick="<carlos:encode value='<%= strOnClick %>' context="javaScriptAttribute"/>">
                    <td>${carlos:forHtml(contact.lastName)}</td>
                    <td>${carlos:forHtml(contact.firstName)}</td>
                    <td>${carlos:forHtml(contact.phoneNumber)}</td>
                </tr>
            </c:forEach>


        </table>

        <%
            int nLastPage = 0, nNextPage = 0;
            nNextPage = Integer.parseInt(strLimit2) + Integer.parseInt(strLimit1);
            nLastPage = Integer.parseInt(strLimit1) - Integer.parseInt(strLimit2);
        %>
        <%
        if (nItems == 0 && nLastPage <= 0) {
        %>
        <fmt:message key="demographic.search.noResultsWereFound"/>
        <%
        }
    %>
        <script language="JavaScript">
            <!--
            function last() {
                <c:set var="__enc_1"><carlos:encode value='<%= form %>' context="uriComponent"/></c:set>
                <c:set var="__enc_2"><carlos:encode value='<%= elementName %>' context="uriComponent"/></c:set>
                <c:set var="__enc_3"><carlos:encode value='<%= elementId %>' context="uriComponent"/></c:set>
                <c:set var="__enc_4"><carlos:encode value='<%= StringUtils.noNull(request.getParameter("keyword")) %>' context="uriComponent"/></c:set>
                <c:set var="__enc_5"><carlos:encode value='<%= StringUtils.noNull(request.getParameter("search_mode")) %>' context="uriComponent"/></c:set>
                <c:set var="__enc_6"><carlos:encode value='<%= StringUtils.noNull(request.getParameter("orderby")) %>' context="uriComponent"/></c:set>
                document.nextform.action = "<%= request.getContextPath() %>/demographic/ViewProfessionalSpecialistSearch?form=<carlos:encode value='${__enc_1}' context="javaScript"/>&elementName=<carlos:encode value='${__enc_2}' context="javaScript"/>&elementId=<carlos:encode value='${__enc_3}' context="javaScript"/>&keyword=<carlos:encode value='${__enc_4}' context="javaScript"/>&search_mode=<carlos:encode value='${__enc_5}' context="javaScript"/>&orderby=<carlos:encode value='${__enc_6}' context="javaScript"/>&limit1=<%=nLastPage%>&limit2=<carlos:encode value='<%= strLimit2 %>' context="javaScript"/>";
                document.nextform.submit();
            }

            function next() {
                <c:set var="__enc_7"><carlos:encode value='<%= form %>' context="uriComponent"/></c:set>
                <c:set var="__enc_8"><carlos:encode value='<%= elementName %>' context="uriComponent"/></c:set>
                <c:set var="__enc_9"><carlos:encode value='<%= elementId %>' context="uriComponent"/></c:set>
                <c:set var="__enc_10"><carlos:encode value='<%= StringUtils.noNull(request.getParameter("keyword")) %>' context="uriComponent"/></c:set>
                <c:set var="__enc_11"><carlos:encode value='<%= StringUtils.noNull(request.getParameter("search_mode")) %>' context="uriComponent"/></c:set>
                <c:set var="__enc_12"><carlos:encode value='<%= StringUtils.noNull(request.getParameter("orderby")) %>' context="uriComponent"/></c:set>
                document.nextform.action = "<%= request.getContextPath() %>/demographic/ViewProfessionalSpecialistSearch?form=<carlos:encode value='${__enc_7}' context="javaScript"/>&elementName=<carlos:encode value='${__enc_8}' context="javaScript"/>&elementId=<carlos:encode value='${__enc_9}' context="javaScript"/>&keyword=<carlos:encode value='${__enc_10}' context="javaScript"/>&search_mode=<carlos:encode value='${__enc_11}' context="javaScript"/>&orderby=<carlos:encode value='${__enc_12}' context="javaScript"/>&limit1=<%=nNextPage%>&limit2=<carlos:encode value='<%= strLimit2 %>' context="javaScript"/>";
                document.nextform.submit();
            }

            //-->
        </SCRIPT>

        <form method="post" name="nextform" action="<%= request.getContextPath() %>/demographic/ViewProfessionalSpecialistSearch">
            <%
                if (nLastPage >= 0) {
            %> <input type="submit" class="mbttn" name="submit"
                      value="<fmt:message key="demographic.demographicsearch2apptresults.btnPrevPage"/>"
                      onClick="last()"> <%
            }
            if (nItems == Integer.parseInt(strLimit2)) {
        %> <input type="submit" class="mbttn" name="submit"
                  value="<fmt:message key="demographic.demographicsearch2apptresults.btnNextPage"/>"
                  onClick="next()"> <%
            }
        %>
        </form>
        <br>
        <a href="<%=request.getContextPath() %>/encounter/oscarConsultationRequest/config/ViewShowAllServices">Add/Edit
            Professional Specialist</a></center>
    </body>
</html>
