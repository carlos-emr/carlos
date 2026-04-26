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
<%--
    Ontario Billing clipboard print preview. Receives `textfield` and
    `textfield1` POST parameters from billingClipboard.jsp and renders
    them as a printable <pre> block. ViewPrintBillingClipboard2Action
    enforces _billing r.

    @since 2006
--%>
<%@ page errorPage="/WEB-INF/jsp/error/errorpage.jsp" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.functions" prefix="fn" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">
<html>
<head>
    <script type="text/javascript" src="${pageContext.request.contextPath}/js/global.js"></script>
    <title>oscarBilling :: Clip board ::</title>
    <link rel="stylesheet" href="billing.css">

    <link rel="stylesheet" type="text/css" media="all" href="${pageContext.request.contextPath}/share/css/extractedFromPages.css"/>
    <script language="JavaScript">
        <!--

        function selectprovider(s) {
            if (self.location.href.lastIndexOf("&providerview=") > 0) a = self.location.href.substring(0, self.location.href.lastIndexOf("&providerview="));
            else a = self.location.href;
            self.location.href = a + "&providerview=" + s.options[s.selectedIndex].value;
        }

        function refresh() {
            var u = self.location.href;
            if (u.lastIndexOf("view=1") > 0) {
                self.location.href = u.substring(0, u.lastIndexOf("view=1")) + "view=0" + u.substring(eval(u.lastIndexOf("view=1") + 6));
            } else {
                history.go(0);
            }
        }


        //-->
    </script>
</head>

<body leftmargin="0" topmargin="5" rightmargin="0">
<table width="100%" border="0" cellspacing="0" cellpadding="0">
    <tr bgcolor="#000000">
        <td height="40" width="20%">
            <form><input class=mbttn type=button name=print value=PRINT
                         onClick=window.print()><input class=mbttn type=button
                                                       name=back value=BACK onClick="history.go(-1);return false;">
            </form>
        </td>
        <td width="80%" align="left" bgcolor="#000000">
            <p><font face="Verdana, Arial, Helvetica, sans-serif"
                     color="#FFFFFF"><b><font
                    face="Arial, Helvetica, sans-serif" size="4">oscar<font
                    size="3">Billing</font></font></b></font> <font color="#CCCCCC">Ciipboard </font></p>
        </td>
    </tr>
</table>


<pre>
<carlos:encode value="${param['textfield']}" context="html"/>
</pre>

<pre>
<c:set var="tmp1Value" value="${empty param['textfield1'] ? '' : param['textfield1']}"/>
<c:set var="tmp1Len" value="${fn:length(tmp1Value)}"/>
<c:forEach begin="0" end="${tmp1Len / 80}" varStatus="loop">
    <c:set var="lineStart" value="${loop.index * 80}"/>
    <c:if test="${lineStart < tmp1Len}"><c:set var="lineEnd" value="${lineStart + 80 > tmp1Len ? tmp1Len : lineStart + 80}"/><carlos:encode value="${fn:substring(tmp1Value, lineStart, lineEnd)}" context="html"/>
</c:if></c:forEach>
</pre>


</body>
</html>
