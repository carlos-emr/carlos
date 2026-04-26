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
<%@ page errorPage="/WEB-INF/jsp/error/errorpage.jsp" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%@ page import="io.github.carlos_emr.carlos.billings.ca.on.data.BillingReportControlViewModel" %>
<%@ page import="io.github.carlos_emr.carlos.billings.ca.on.pageUtil.BillingReportControlDataAssembler" %>
<%@ page import="io.github.carlos_emr.carlos.managers.SecurityInfoManager" %>
<%@ page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%
    // Defensive top-of-page model resolver: callers that forward into this JSP
    // directly (without going through ViewBillingReportControl2Action) get the
    // privilege check + assembler re-run inline so the body can stay 100% EL.
    if (request.getAttribute("billingReportControlModel") == null) {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        SecurityInfoManager sec = SpringUtils.getBean(SecurityInfoManager.class);
        if (!sec.hasPrivilege(loggedInInfo, "_report", "r", null)) {
            throw new SecurityException("missing required sec object (_report)");
        }
        request.setAttribute("billingReportControlModel",
                new BillingReportControlDataAssembler().assemble(request));
    }
%>
<html>
<head>
    <script type="text/javascript" src="${pageContext.request.contextPath}/js/global.js"></script>
    <title>Billing Report</title>

    <link rel="stylesheet" type="text/css" media="all" href="${pageContext.request.contextPath}/share/css/extractedFromPages.css"/>
    <script language="JavaScript">
        <!--

        function selectprovider(s) {
            if (self.location.href.lastIndexOf("&providerview=") > 0) a = self.location.href.substring(0, self.location.href.lastIndexOf("&providerview="));
            else a = self.location.href;
            self.location.href = a + "&providerview=" + s.options[s.selectedIndex].value;
        }

        function openBrWindow(theURL, winName, features) { //v2.0
            window.open(theURL, winName, features);
        }

        function refresh() {
            history.go(0);

        }

        //-->
    </script>
</head>

<body bgcolor="#FFFFFF" text="#000000" leftmargin="0" rightmargin="0"
      topmargin="10">
<table width="100%" border="0" cellspacing="0" cellpadding="0">
    <tr>
        <td align="right"><a href=#
                             onClick="popupPage(700,720,'${pageContext.request.contextPath}/oscarReport/ViewManageProvider?action=billingreport')">
            <font size="1">Manage Provider List </font></a></td>
    </tr>
</table>

<table width="100%" border="0" cellspacing="0" cellpadding="0">
    <tr bgcolor="#000000">
        <td height="40" width="10%"></td>
        <td width="90%" align="left">
            <p><b><font face="Verdana, Arial" color="#FFFFFF" size="3">oscarBilling</font></b></p>
        </td>
    </tr>
</table>

<table width="100%" border="0" bgcolor="#EEEEFF">
    <form name="serviceform" method="post"
          action="${pageContext.request.contextPath}/billing/CA/ON/ViewBillingReportControl">
        <tr>
            <td width="50%" align="right"><font size="2" color="#333333"
                                                face="Verdana, Arial, Helvetica, sans-serif"> <input
                    type="radio" name="reportAction" value="unbilled"
                    ${billingReportControlModel.reportAction == 'unbilled' ? 'checked' : ''}>Unbilled <input
                    type="radio" name="reportAction" value="billed"
                    ${billingReportControlModel.reportAction == 'billed' ? 'checked' : ''}>Billed <input
                    type="radio" name="reportAction" value="unsettled"
                    ${billingReportControlModel.reportAction == 'unsettled' ? 'checked' : ''}>Unsettled
                <input type="radio" name="reportAction" value="billob"
                       ${billingReportControlModel.reportAction == 'billob' ? 'checked' : ''}>OB <input
                        type="radio" name="reportAction" value="flu"
                       ${billingReportControlModel.reportAction == 'flu' ? 'checked' : ''}>FLU</font></td>
            <td width="30%" align="right" nowrap><font
                    face="Verdana, Arial, Helvetica, sans-serif" size="2" color="#333333">
                <b>Select provider </b></font> <select name="providerview">
                <c:forEach var="opt" items="${billingReportControlModel.providerOptions}">
                    <option value="<carlos:encode value='${opt.providerNo}' context='htmlAttribute'/>"
                            ${billingReportControlModel.providerView == opt.providerNo ? 'selected' : ''}>
                        <carlos:encode value='${opt.lastName}' context='html'/>,
                        <carlos:encode value='${opt.firstName}' context='html'/>
                    </option>
                </c:forEach>
            </select></td>
            <td align="center"><font color="#333333" size="2"
                                     face="Verdana, Arial, Helvetica, sans-serif"> <input
                    type="hidden" name="verCode" value="V03"> <input
                    type="submit" name="Submit" value="Create Report"> </font></td>
        </tr>
        <tr>
            <td></td>
            <td align="right"><B>Date</B> &nbsp; <font size="1"
                                                       face="Arial, Helvetica, sans-serif"> <a href="#"
                                                                                               onClick="openBrWindow('${pageContext.request.contextPath}/billing/CA/ON/ViewBillingCalendarPopup?type=admission&amp;year=${billingReportControlModel.curYear}&amp;month=${billingReportControlModel.curMonth}','','width=300,height=300')">From:</a></font>
                <input type="text" name="xml_vdate" size="10" value="<carlos:encode value='${billingReportControlModel.xmlVdate}' context='htmlAttribute'/>">
                <font size="1" face="Arial, Helvetica, sans-serif"> <a href="#"
                                                                       onClick="openBrWindow('${pageContext.request.contextPath}/billing/CA/ON/ViewBillingCalendarPopup?type=end&amp;year=${billingReportControlModel.curYear}&amp;month=${billingReportControlModel.curMonth}','','width=300,height=300')">
                    To:</a></font> <input type="text" name="xml_appointment_date" size="10"
                                          value="<carlos:encode value='${billingReportControlModel.xmlAppointmentDate}' context='htmlAttribute'/>"></td>
            <td></td>
        </tr>
    </form>
</table>

<c:choose>
    <c:when test="${empty billingReportControlModel.reportAction}">
        <p>&nbsp;</p>
    </c:when>
    <c:when test="${billingReportControlModel.reportAction == 'unbilled'}">
        <%@ include file="billingReport_unbilled.jspf" %>
    </c:when>
    <c:when test="${billingReportControlModel.reportAction == 'billed'}">
        <%@ include file="billingReport_billed.jspf" %>
    </c:when>
    <c:when test="${billingReportControlModel.reportAction == 'unsettled'}">
        <%@ include file="billingReport_unsettled.jspf" %>
    </c:when>
    <c:when test="${billingReportControlModel.reportAction == 'billob'}">
        <%@ include file="billingReport_billob.jspf" %>
    </c:when>
    <c:when test="${billingReportControlModel.reportAction == 'flu'}">
        <%@ include file="billingReport_flu.jspf" %>
    </c:when>
</c:choose>

<br>

<hr width="100%">
<table border="0" cellspacing="0" cellpadding="0" width="100%">
    <tr>
        <td><a href=# onClick="javascript:history.go(-1);return false;">
            <img src="images/leftarrow.gif" border="0" width="25" height="20"
                 align="absmiddle"> Back </a></td>
        <td align="right"><a href="" onClick="self.close();">Close
            the Window<img src="images/rightarrow.gif" border="0" width="25"
                           height="20" align="absmiddle"></a></td>
    </tr>
</table>

</body>
</html>
