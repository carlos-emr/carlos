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

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_report,_admin.reporting" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_report&type=_admin.reporting");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>

<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>
<%


    String demographic_no = request.getParameter("demographic_no") != null ? request.getParameter("demographic_no") : "0";
    String deepcolor = "#CCCCFF", weakcolor = "#EEEEFF";
    int historyOffset = request.getAttribute("historyOffset") instanceof Integer ? (Integer) request.getAttribute("historyOffset") : 0;
    int historyPageSize = request.getAttribute("historyPageSize") instanceof Integer ? (Integer) request.getAttribute("historyPageSize") : 10;
%>
<%@ page import="java.lang.*, io.github.carlos_emr.carlos.providers.data.*"
         errorPage="/WEB-INF/jsp/error/errorpage.jsp" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.EChart" %>
<%@ page import="io.github.carlos_emr.carlos.providers.data.ProviderData" %>

<html>
<head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
    <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
    <title><fmt:message key="encounter.echartHistory.title"/></title>
    <link rel="stylesheet" href="<%= request.getContextPath() %>/web.css">
    <script language="JavaScript">
        <!--
        function setfocus() {
            this.focus();
            //document.titlesearch.keyword.select();
        }

        //-->
    </SCRIPT>
</head>
<body bgproperties="fixed" onLoad="setfocus()" topmargin="0"
      leftmargin="0" rightmargin="0">

<p><strong><fmt:message key="encounter.historicalChart.title"/></strong><br/>
    <fmt:message key="encounter.historicalChart.notice"/>
</p>
<table border="0" cellspacing="0" cellpadding="0" width="100%">
    <tr bgcolor="#CCCCFF">
        <th align=CENTER NOWRAP><font face="Helvetica"><fmt:message key="encounter.echartHistory.title"/></font></th>
        <th width="10%" nowrap><input type="button" name="Button"
                                      value="<fmt:message key="encounter.echartHistory.buttonPrint"/>"
                                      onClick="window.print()"><input type="button" name="Button"
                                                                      value="<fmt:message key="encounter.echartHistory.buttonExit"/>"
                                                                      onClick="window.close()"></th>
    </tr>
</table>

<table width="480" border="0" cellspacing="1" cellpadding="0">
    <tr>
        <td></td>
        <td align="right"></td>
    </tr>
</table>
<table width="100%" border="0" bgcolor="#ffffff" cellspacing="1"
       cellpadding="2">
    <tr bgcolor="#CCCCFF" align="center">
        <TH><b><fmt:message key="encounter.echartHistory.apptDate"/></b></TH>
        <TH width="50%"><b><fmt:message key="encounter.echartHistory.reason"/></b></TH>
        <!--TH width="10%"><b>Size</b></TH-->
        <th><fmt:message key="encounter.echartHistory.provider"/></th>
    </tr>
    <%
        boolean bfirsttime = true;
        int nItems = 0;
        int ectsize = 0;
        String datetime = null;
        String splitectsize = request.getParameter("splitectsize") != null ? request.getParameter("splitectsize") : "-";
        String bgcolor = null;



        for (EChart eChart : request.getAttribute("historyRows") == null ? java.util.List.<EChart>of() : (java.util.List<EChart>) request.getAttribute("historyRows")) {

            //bodd = bodd?false:true;
            nItems++;
            bgcolor = nItems % 2 == 0 ? "#EEEEFF" : "white";
            datetime = eChart.getTimestamp() == null ? String.valueOf(eChart.getId()) : eChart.getTimestamp().toString();
            ectsize = eChart.getEncounter() == null ? 0 : eChart.getEncounter().length() / 1024;

            if (eChart.getSubject() != null && eChart.getSubject().equals("SPLIT CHART")) {
                splitectsize = "" + ectsize;
                bgcolor = "gold";
            }
    %>
    <tr bgcolor="<%=bgcolor%>">
        <td align="center"><a
                href="<%= request.getContextPath() %>/encounter/ViewEcharthistoryprint?echartid=<%=eChart.getId()%>&demographic_no=<carlos:encode value='<%= demographic_no %>' context="uriComponent"/>"><carlos:encode value='<%= datetime %>' context="html"/>
        </a></td>
        <td><carlos:encode value='<%= eChart.getSubject() != null ? eChart.getSubject() : "" %>' context="html"/>
        </td>
        <!--td align="center"><%--=ectsize + "KB" --%></td-->
        <td><carlos:encode value='<%= ProviderData.getProviderName(eChart.getProviderNo()) %>' context="html"/>
        </td>
    </tr>
    <%
        }
    %>

</table>
<br>
<CENTER>
    <%
        int nLastPage = Math.max(0, historyOffset - historyPageSize);
        long nNextPage = (long) historyOffset + historyPageSize;
        int intLimit2 = historyPageSize;
        if (historyOffset > 0) {
    %> <a
        href="<%= request.getContextPath() %>/report/ViewReportecharthistory?demographic_no=<carlos:encode value='<%= demographic_no %>' context="uriComponent"/>&limit1=<%=nLastPage%>&limit2=<%=intLimit2%>"><fmt:message key="report.reportecharthistory.lastPage"/></a> | <%
    }
    if (Boolean.TRUE.equals(request.getAttribute("historyHasNext"))) {
%> <a
        href="<%= request.getContextPath() %>/report/ViewReportecharthistory?demographic_no=<carlos:encode value='<%= demographic_no %>' context="uriComponent"/>&limit1=<%=nNextPage%>&limit2=<%=intLimit2%>&splitectsize=<carlos:encode value='<%= splitectsize %>' context="uriComponent"/>">
    <fmt:message key="report.reportecharthistory.nextPage"/></a> <%
    }
%>
</CENTER>
</body>
</html>
