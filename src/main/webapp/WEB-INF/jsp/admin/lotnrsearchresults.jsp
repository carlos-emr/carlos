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

<%@page import="java.net.URLEncoder" %>
<%@page import="java.nio.charset.StandardCharsets" %>
<%@page import="java.text.SimpleDateFormat, java.util.*,io.github.carlos_emr.carlos.prevention.*,io.github.carlos_emr.carlos.util.*" %>
<%@page import="org.owasp.encoder.Encode" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>

<%@ taglib uri="/WEB-INF/caisi-tag.tld" prefix="caisi" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.PreventionsLotNrs" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.PreventionsLotNrsDao" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="java.util.*" %>
<%@ page import="io.github.carlos_emr.CarlosProperties" %>
<%@ page import="org.springframework.web.context.support.WebApplicationContextUtils" %>
<%
    String orderby = request.getParameter("orderby") != null ? request.getParameter("orderby") : "prevention_type";
    String deepcolor = "#CCCCFF", weakcolor = "#EEEEFF";
%>
<%@ page import="java.sql.*, java.util.*, io.github.carlos_emr.*" buffer="none"
         errorPage="/WEB-INF/jsp/error/errorpage.jsp" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SafeEncode" %>
<jsp:useBean id="apptMainBean" class="io.github.carlos_emr.AppointmentMainBean"
             scope="session"/>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="carlos" prefix="carlos" %>

<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>

<security:oscarSec roleName="<%=roleName$%>" objectName="_admin" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_admin");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>


<html>
    <head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title><fmt:message key="admin.lotnrsearchresults.title"/></title>
        <link rel="stylesheet" href="<%= request.getContextPath() %>/web.css"/>
        <script LANGUAGE="JavaScript">
            <!--
            function setfocus() {
                document.searchlotnr.keyword.focus();
                document.searchlotnr.keyword.select();
            }

            function onsub() {
                var keyword = document.searchlotnr.keyword.value;
                document.searchlotnr.keyword.value = keyword.toLowerCase();
            }

            //-->
        </script>
    </head>
    <body onLoad="setfocus()" topmargin="0" leftmargin="0" rightmargin="0">
    <center>
        <table border="0" cellspacing="0" cellpadding="0" width="100%">
            <tr bgcolor="<%=deepcolor%>">
                <th><fmt:message key="admin.lotnrsearchresults.description"/></th>
            </tr>
        </table>
        <table cellspacing="0" cellpadding="0" width="100%" border="0"
               BGCOLOR="<%=weakcolor%>">
            <form method="post" action="${pageContext.request.contextPath}/admin/LotNrSearchResults" name="searchlotnr"
                  onsubmit="return onsub();">
                <tr valign="top">
                    <td rowspan="2" align="right" valign="middle"><font
                            face="Verdana" color="#0000FF"><b><i><fmt:message key="admin.search.formSearchCriteria"/></i></b></font></td>
                    <td nowrap><font size="1" face="Verdana" color="#0000FF">
                        <input type="radio"
                                <%="search_prev".equals(request.getParameter("search_mode"))?"checked":""%>
                               name="search_mode" value="search_prev"
                               onclick="document.forms['searchlotnr'].keyword.focus();"><fmt:message key="admin.lotnrsearch.prevention"/></font></td>
                    <td valign="middle" rowspan="2" ALIGN="left"><input type="text"
                                                                        NAME="keyword" SIZE="17" MAXLENGTH="100"
                                                                        value="<%=request.getParameter("keyword") != null ? SafeEncode.forHtmlAttribute(request.getParameter("keyword")) : ""%>"><%-- nosemgrep: java.jsp.jsp-scriptlet-xss.jsp-scriptlet-xss --%>
                        <INPUT
                                TYPE="hidden" NAME="orderby" VALUE="prevention_type"> <INPUT
                                TYPE="hidden" NAME="dboperation" VALUE="lotnr_search_prevention">
                        <INPUT TYPE="hidden" NAME="limit1" VALUE="0"> <INPUT
                                TYPE="hidden" NAME="limit2" VALUE="10"> <INPUT
                                TYPE="SUBMIT" NAME="button"
                                VALUE=
                                    <fmt:message key="admin.lotnrsearchresults.btnSubmit"/>
                                        SIZE="17"></td>
                </tr>
            </form>
        </table>

        <table width="100%" border="0">
            <tr>
                <td align="left"><i><fmt:message key="admin.search.keywords"/></i>
                    : <carlos:encode value='<%= request.getParameter("keyword") != null ? request.getParameter("keyword") : "" %>' context="html"/><%-- nosemgrep: java.jsp.jsp-scriptlet-xss.jsp-scriptlet-xss --%>
                </td>
            </tr>
        </table>

        <CENTER>
            <table width="100%" cellspacing="2" cellpadding="2" border="0"
                   bgcolor="ivory">
                <tr bgcolor="<%=deepcolor%>">
                    <TH align="center" width="25%"><b><fmt:message key="admin.lotnrsearchresults.prevention"/></b></TH>
                    <TH align="center" width="25%"><b><fmt:message key="admin.lotnrsearchresults.lotnr"/> </b></TH>
                </tr>


                <%
                    PreventionsLotNrsDao PreventionsLotNrsDao = (PreventionsLotNrsDao) SpringUtils.getBean(PreventionsLotNrsDao.class);
                    int nItems = 0;
                    boolean bodd = false;
                    String keyword = request.getParameter("keyword").trim();
                    String prevention = keyword + "%";
                    //find active lot number records only
                    List<PreventionsLotNrs> p = PreventionsLotNrsDao.findPagedData(prevention, false, Integer.parseInt(request.getParameter("limit1")), Integer.parseInt(request.getParameter("limit2")));
                    for (PreventionsLotNrs pRec : p) {
                        bodd = bodd ? false : true;
                        nItems++;
                %>
                <tr bgcolor="<%=bodd?"white":weakcolor%>">
                    <td><carlos:encode value='<%= pRec.getPreventionType() %>' context="html"/>
                    </td>
                    <td><a
                     href="${pageContext.request.contextPath}/admin/ViewLotNrDeleteRecordHtm?prevention=<carlos:encode value='<%= pRec.getPreventionType() %>' context="uriComponent"/>&lotnr=<%=URLEncoder.encode(pRec.getLotNr(), StandardCharsets.UTF_8)%>"><carlos:encode value='<%= pRec.getLotNr() %>' context="html"/>
                    </a></td>
                </tr>
                <% }
                %>

            </table>
            <br>
            <%
                int nLastPage = 0, nNextPage = 0;
                String strLimit1 = request.getParameter("limit1");
                String strLimit2 = request.getParameter("limit2");

                int limit1 = Integer.parseInt(strLimit1);
                int limit2 = Integer.parseInt(strLimit2);
                nNextPage = limit2 + limit1;
                nLastPage = limit1 - limit2;
                if (nLastPage >= 0) {
            %> <a
                href="${pageContext.request.contextPath}/admin/LotNrSearchResults?keyword=<carlos:encode value='<%= request.getParameter("keyword") != null ? request.getParameter("keyword") : "" %>' context="uriComponent"/>&search_mode=<carlos:encode value='<%= request.getParameter("search_mode") != null ? request.getParameter("search_mode") : "" %>' context="uriComponent"/>&limit1=<%=nLastPage%>&limit2=<%=limit2%>"><fmt:message key="admin.lotnrsearchresults.btnLastPage"/></a> | <%-- nosemgrep: java.jsp.jsp-scriptlet-xss.jsp-scriptlet-xss --%><%
            }
            if (nItems == limit2) {
        %> <a
                href="${pageContext.request.contextPath}/admin/LotNrSearchResults?keyword=<carlos:encode value='<%= request.getParameter("keyword") != null ? request.getParameter("keyword") : "" %>' context="uriComponent"/>&search_mode=<carlos:encode value='<%= request.getParameter("search_mode") != null ? request.getParameter("search_mode") : "" %>' context="uriComponent"/>&limit1=<%=nNextPage%>&limit2=<%=limit2%>"><fmt:message key="admin.lotnrsearchresults.btnNextPage"/></a> <%-- nosemgrep: java.jsp.jsp-scriptlet-xss.jsp-scriptlet-xss --%><%
            }
        %>
            <p><fmt:message key="admin.lotnrsearchresults.msgClickForEditing"/></p>
            <br/>
            <a href="${pageContext.request.contextPath}/admin/ViewLotNrAddRecordHtm">Add new Lot #</a>
        </center>
    </body>
</html>
