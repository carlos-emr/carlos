<%--
    Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
    Copyright (c) 2008-2012 Indivica Inc.

    This software is published under the GPL GNU General Public License.
    This program is free software; you can redistribute it and/or
    modify it under the terms of the GNU General Public License
    as published by the Free Software Foundation; version 2
    of the License.

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
  Purpose: Display the Ontario MOH XML report selected by the paired action.
  Features: transforms report XML with its selected XSL stylesheet and supports printing.
  Parameters: lreportModel request attribute supplies filename, xslName and fileContents.
  @since 2026-07-07
  Keep request setup in the paired action and use CARLOS encoding helpers
  for dynamic output rendered by the page.
--%>
<%@ page language="java" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>


<%@ page errorPage="/WEB-INF/jsp/error/errorpage.jsp" %>
<%--
    File-read + XSL-name resolution moved to BillingLegacyReport2Action; the
    ${lreportModel} request attribute now exposes filename, xslName, and
    fileContents. _admin.billing w is enforced by the action.
--%>

<html>
    <head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
        <script type="text/javascript" src="${pageContext.request.contextPath}/js/global.js"></script>
        <title>MOH Report</title>
        <link rel="stylesheet" href="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/billing/CA/ON/billing.css">
        <link rel="stylesheet" type="text/css" media="all"
              href="${pageContext.request.contextPath}/share/css/extractedFromPages.css"/>

        <script src="${pageContext.request.contextPath}/js/billing-moh-report.js" defer></script>

        <style>
            @media print {
                .noprint {
                    display: none !important;
                }
            }
        </style>
    </head>

    <body>
    <table width="100%" border="0" cellspacing="0" cellpadding="0" class="noprint">
        <tr>
            <td height="40" width="10%" class="Header">
                <font size="3">Billing</font>
            </td>
            <td width="90%" align="right" class="Header">
                <input type="button" name="print" value="<fmt:message key="global.btnPrint"/>"
                       onClick="window.print()">
            </td>
        </tr>
    </table>
    <div id="MOHreportError" role="alert" hidden>Could not display the selected MOH report. Check that the file is a valid MOH XML report and try again.</div>
    <div id="MOHreport"></div>
    <textarea id="MOHreportSource" hidden
              data-stylesheet="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/billing/CA/ON/${carlos:forHtmlAttribute(lreportModel.xslName)}.xsl"><carlos:encode value="${lreportModel.fileContents}"/></textarea>

    </body>
</html>

