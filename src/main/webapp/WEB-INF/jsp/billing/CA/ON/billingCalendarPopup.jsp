<%--
    Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
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

    CARLOS EMR Project
    https://github.com/carlos-emr/carlos
--%>
<%--
  Purpose: Supports billingCalendarPopup in the Ontario billing workflow.
  Expected request model data includes: billingCalendarPopupModel.
  Keep request setup in the paired action and use CARLOS encoding helpers
  for dynamic output rendered by the page.
--%>
<%@ page errorPage="/WEB-INF/jsp/error/errorpage.jsp" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%
    // Defensive top-of-page model resolver. The canonical entrypoint is
    // billing/CA/ON/ViewBillingCalendarPopup; any direct forward gets the
    // privilege check + assembler re-run inline so the body can stay 100% EL.
    %>
<fmt:setBundle basename="oscarResources"/>
<html>
<head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
    <script type="text/javascript" src="${pageContext.request.contextPath}/js/global.js"></script>
    <title><fmt:message key="billing.billingCalendarPopup.title"/></title>
    <script language="JavaScript">
        <!--

        function twoDigit(value) {
            value = String(value);
            return value.length < 2 ? "0" + value : value;
        }

        function typeInDate(year1, month1, day1) {
            self.close();
            opener.document.serviceform.xml_vdate.value = year1 + "-" + twoDigit(month1) + "-" + twoDigit(day1);
        }

        function typeSrvDate(year1, month1, day1) {
            self.close();
            opener.document.serviceform.xml_appointment_date.value = year1 + "-" + twoDigit(month1) + "-" + twoDigit(day1);
        }

        //-->
    </script>
</head>

<body bgcolor="ivory" onLoad="setfocus()" leftmargin="0" rightmargin="0">

<table BORDER="0" CELLPADDING="0" CELLSPACING="0" WIDTH="100%">
    <tr BGCOLOR="#FFD7C4">
        <td width="5%" nowrap><a
                href="${pageContext.request.contextPath}/billing/CA/ON/ViewBillingCalendarPopup?year=${billingCalendarPopupModel.yearMinusOne}&month=${billingCalendarPopupModel.month}&delta=0&type=<carlos:encode value='${billingCalendarPopupModel.type}' context='uriComponent'/>">
            <img src="${pageContext.request.contextPath}/billing/CA/ON/images/previous.gif" WIDTH="10" HEIGHT="9" BORDER="0"
                 ALT="Last Year" vspace="2"> <img src="${pageContext.request.contextPath}/billing/CA/ON/images/previous.gif"
                                                  WIDTH="10" HEIGHT="9" BORDER="0" ALT="Last Year" vspace="2"></a>
        </td>
        <td align="center"><a
                href="${pageContext.request.contextPath}/billing/CA/ON/ViewBillingCalendarPopup?year=${billingCalendarPopupModel.year}&month=${billingCalendarPopupModel.month}&delta=-1&type=<carlos:encode value='${billingCalendarPopupModel.type}' context='uriComponent'/>">
            <img src="${pageContext.request.contextPath}/billing/CA/ON/images/previous.gif" WIDTH="10" HEIGHT="9" BORDER="0"
                 ALT="View Last Month" vspace="2"> <fmt:message key="billing.billingCalendarPopup.btnLast"/></a> <b><span
                CLASS=title>${billingCalendarPopupModel.year}-${billingCalendarPopupModel.month}</span></b> <a
                href="${pageContext.request.contextPath}/billing/CA/ON/ViewBillingCalendarPopup?year=${billingCalendarPopupModel.year}&month=${billingCalendarPopupModel.month}&delta=1&type=<carlos:encode value='${billingCalendarPopupModel.type}' context='uriComponent'/>">
            <fmt:message key="billing.billingCalendarPopup.btnNext"/> <img
                src="${pageContext.request.contextPath}/billing/CA/ON/images/next.gif" WIDTH="10" HEIGHT="9" BORDER="0"
                ALT="View Next Month" vspace="2">&nbsp;&nbsp;</a></td>
        <td width="5%" align="right" nowrap><a
                href="${pageContext.request.contextPath}/billing/CA/ON/ViewBillingCalendarPopup?year=${billingCalendarPopupModel.yearPlusOne}&month=${billingCalendarPopupModel.month}&delta=0&type=<carlos:encode value='${billingCalendarPopupModel.type}' context='uriComponent'/>">
            <img src="${pageContext.request.contextPath}/billing/CA/ON/images/next.gif" WIDTH="10" HEIGHT="9" BORDER="0"
                 ALT="Next Year" vspace="2"> <img src="${pageContext.request.contextPath}/billing/CA/ON/images/next.gif"
                                                  WIDTH="10" HEIGHT="9" BORDER="0" ALT="Next Year" vspace="2"></a>
        </td>
    </tr>
</table>

<c:set var="monthLabels" value="Jan,Feb,Mar,Apr,May,Jun,Jul,Aug,Sep,Oct,Nov,Dec"/>
<table width="100%" border="0" cellspacing="0" cellpadding="1">
    <tr align="center" bgcolor="#FFFFFF">
        <th>
            <c:forEach var="label" items="${monthLabels}" varStatus="ms">
                <a href="${pageContext.request.contextPath}/billing/CA/ON/ViewBillingCalendarPopup?year=${billingCalendarPopupModel.year}&month=${ms.index + 1}&delta=0&type=<carlos:encode value='${billingCalendarPopupModel.type}' context='uriComponent'/>">
                    <font SIZE="2" color='${ms.index + 1 == billingCalendarPopupModel.month ? "red" : "blue"}'>${label}</font>
                </a>
            </c:forEach>
        </th>
    </tr>
</table>

<table width="100%" border="1" cellspacing="0" cellpadding="2"
       bgcolor="silver">
    <tr bgcolor="#FOFOFO" align="center">
        <td width="12.5%"><font FACE="VERDANA,ARIAL,HELVETICA" SIZE="2"
                                color="red"><fmt:message key="billing.billingCalendarPopup.msgSun"/></font></td>
        <td width="12.5%"><font FACE="VERDANA,ARIAL,HELVETICA" SIZE="2"><fmt:message key="billing.billingCalendarPopup.msgMon"/></font></td>
        <td width="12.5%"><font FACE="VERDANA,ARIAL,HELVETICA" SIZE="2"><fmt:message key="billing.billingCalendarPopup.msgTue"/></font></td>
        <td width="12.5%"><font FACE="VERDANA,ARIAL,HELVETICA" SIZE="2"><fmt:message key="billing.billingCalendarPopup.msgWed"/></font></td>
        <td width="12.5%"><font FACE="VERDANA,ARIAL,HELVETICA" SIZE="2"><fmt:message key="billing.billingCalendarPopup.msgThu"/></font></td>
        <td width="12.5%"><font FACE="VERDANA,ARIAL,HELVETICA" SIZE="2"><fmt:message key="billing.billingCalendarPopup.msgFri"/></font></td>
        <td width="12.5%"><font FACE="VERDANA,ARIAL,HELVETICA" SIZE="2"
                                color="green"><fmt:message key="billing.billingCalendarPopup.msgSat"/></font></td>
    </tr>

    <c:forEach var="week" items="${billingCalendarPopupModel.weeks}">
        <tr>
            <c:forEach var="cell" items="${week.cells}">
                <c:choose>
                    <c:when test="${cell.day == 0}">
                        <td></td>
                    </c:when>
                    <c:when test="${billingCalendarPopupModel.admission}">
                        <td align="center" bgcolor='#FBECF3'><a href="#"
                                                                onClick="typeInDate(${billingCalendarPopupModel.year},${billingCalendarPopupModel.month},${cell.day})">${cell.day}</a></td>
                    </c:when>
                    <c:otherwise>
                        <td align="center" bgcolor='#FBECF3'><a href="#"
                                                                onClick="typeSrvDate(${billingCalendarPopupModel.year},${billingCalendarPopupModel.month},${cell.day})">${cell.day}</a></td>
                    </c:otherwise>
                </c:choose>
            </c:forEach>
        </tr>
    </c:forEach>

</table>

<table width="100%" border="0" cellspacing="0" cellpadding="0">
    <tr>
        <td bgcolor="#FFD7C4" align="center"><input type="button"
                                                    name="Cancel"
                                                    value=" <fmt:message key="billing.billingCalendarPopup.btnExit"/> "
                                                    onClick="window.close()"></td>
    </tr>
</table>

</body>
</html>
