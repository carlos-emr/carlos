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
<!DOCTYPE html>
<%@page errorPage="/WEB-INF/jsp/error/errorpage.jsp" %>
<%@ page import="io.github.carlos_emr.carlos.util.StringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.utility.MiscUtils" %>
<%@ page import="io.github.carlos_emr.carlos.billings.ca.on.data.BillingDigSearchViewModel" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>

<%
    // ViewBillingDigSearch2Action enforces _billing r and assembles the
    // view model with the DiagnosticCodeDao lookups the JSP body used to
    // perform inline.
    BillingDigSearchViewModel digSearchModel =
            (BillingDigSearchViewModel) request.getAttribute("digSearchModel");
    if (digSearchModel == null) {
        MiscUtils.getLogger().warn(
                "billingDigSearch.jsp reached without digSearchModel — caller "
              + "should route through billing/CA/ON/ViewBillingDigSearch.");
        digSearchModel = BillingDigSearchViewModel.builder().build();
    }

    // Extract form index + element name from a full JS path like
    // "document.forms[0].elements['fieldname'].value" (format used by billingON.jsp callers)
    // Allows dots in element names (e.g. "pref.default_dx_code" from UserPreferences.jsp)
    String name2 = request.getParameter("name2");
    String targetFormIdx = null;
    String targetElement = null;
    boolean name2ParseError = false;
    if (name2 != null) {
        java.util.regex.Matcher m2 = java.util.regex.Pattern
            .compile("^document\\.forms\\[(\\d+)\\]\\.elements\\['([a-zA-Z0-9_.]+)'\\]\\.value$")
            .matcher(name2);
        if (m2.matches()) {
            targetFormIdx = m2.group(1);
            targetElement = m2.group(2);
        } else if (!name2.isEmpty()) {
            String truncated = name2.length() > 120 ? name2.substring(0, 120) + "..." : name2;
            MiscUtils.getLogger().warn("billingDigSearch.jsp: 'name2' did not match expected JS path format: '"
                + truncated + "' (length=" + name2.length() + ")");
            name2ParseError = true;
        }
    }
%>

<html>
    <head>
        <title><fmt:message key="billing.billingDigSearch.title"/></title>
        <script type="text/javascript" src="${pageContext.request.contextPath}/js/global.js"></script>
        <link href="${pageContext.request.contextPath}/library/bootstrap/5.3.8/css/bootstrap.min.css" rel="stylesheet">
        <script>
            function CodeAttach(File2) {
                if (self.opener.callChangeCodeDesc) self.opener.callChangeCodeDesc();

                <%if(targetElement != null) {%>
                self.opener.document.forms[<%= targetFormIdx %>].elements["<carlos:encode value='<%= StringUtils.noNull(targetElement) %>' context="javaScriptBlock"/>"].value = File2.substring(0, 3);
                <%} else if(name2ParseError) {%>
                alert("Error: Unable to transfer diagnostic code to the billing form. Please close this window and try again.");
                return;
                <%} else {%>
                self.opener.document.forms[1].xml_diagnostic_detail.value = File2;
                <%}%>
                setTimeout("self.close();", 100);
            }

            function setfocus() {
                this.focus();
                document.forms[0].codedesc.focus();
                document.forms[0].codedesc.select();
            }
        </script>

    </head>

    <body onLoad="setfocus()">
    <%if(name2ParseError) {%>
    <script>alert("Warning: The diagnostic code field reference could not be parsed. Selecting a code may not work correctly. Please close this window and try again from the billing form.");</script>
    <%}%>
    <table style="width:100%">
        <tr>
            <th style="text-align:center; background-color:silver;"><fmt:message key="billing.billingDigSearch.msgDiagnostic"/><fmt:message key="billing.billingDigSearch.msgMaxSelections"/></th>
        </tr>
    </table>

    <form name="codesearch" id="codesearch" method="post"
          action="<%= request.getContextPath() %>/billing/CA/ON/ViewBillingDigSearch">
        <%if (targetElement != null || name2ParseError) {%>
        <input type="hidden" name="name2"
               value="<carlos:encode value='<%= name2 %>' context="htmlAttribute"/>"/>
        <%}%>
        <p><b><fmt:message key="billing.billingDigSearch.msgRefine"/></b><br>
            <fmt:message key="billing.billingDigSearch.msgCodeRange"/>: <select
                    name="coderange">
                <option value="0" selected>000-099</option>
                <option value="1">100-199</option>
                <option value="2">200-299</option>
                <option value="3">300-399</option>
                <option value="4">400-499</option>
                <option value="5">500-599</option>
                <option value="6">600-699</option>
                <option value="7">700-799</option>
                <option value="8">800-899</option>
                <option value="9">900-999</option>
            </select> <fmt:message key="billing.billingDigSearch.msgOR"/> <br/>
            <fmt:message key="billing.billingDigSearch.msgDescription"/>: <input
                    type="text" name="codedesc" value=""> <input type="submit" class="btn btn-secondary"
                                                                 name="search1"
                                                                 value="<fmt:message key="billing.billingDigSearch.btnSearch"/>"/>
        </p>
        <input type="hidden" name="search"
               value="<fmt:message key="billing.billingDigSearch.btnSearch"/>"/>
    </form>

    <form name="diagcode" id="diagcode" method="post"
          action="<%= request.getContextPath() %>/billing/CA/ON/BillingDigUpdate">
        <table style="width:800px; margin:auto" class="table-striped table-sm">
            <thead>
            <tr>
                <th style="width:12%"><b><fmt:message key="billing.billingDigSearch.formCode"/></b></th>
                <th style="width:88%"><b><fmt:message key="billing.billingDigSearch.formDescription"/></b></th>
            </tr>
            </thead>
            <tbody>
            <% for (BillingDigSearchViewModel.DxRow __row : digSearchModel.getRows()) { %>
            <tr>
                <td style="width:12%"><a
                        href="javascript:CodeAttach('<carlos:encode value='<%= __row.code() %>' context="javaScriptAttribute"/>|<carlos:encode value='<%= __row.description() %>' context="javaScriptAttribute"/>')"><carlos:encode value='<%= __row.code() %>' context="html"/>
                </a></td>
                <td style="width:88%"><input type="text" class="form-control" style="margin-bottom: 0px;"
                                             name="<carlos:encode value='<%= __row.code() %>' context="htmlAttribute"/>"
                                             value="<carlos:encode value='<%= __row.description() %>' context="htmlAttribute"/>">&nbsp;<input type="submit" class="btn btn-secondary"
                                                                                 name="update"
                                                                                 value="<fmt:message key="billing.billingDigSearch.btnUpdate"/> <carlos:encode value='<%= __row.code() %>' context="html"/>">
                </td>
            </tr>
            <% } %>

            <% if (digSearchModel.isNoMatch()) { %>
            <tr>
                <td colspan="2"><fmt:message key="billing.billingDigSearch.msgNoMatch"/>.</td>
            </tr>
            <% } %>

            <% if (digSearchModel.isAutoSelect()) { %>
            <script LANGUAGE="JavaScript">
                <!--
                CodeAttach('<carlos:encode value='<%= digSearchModel.getAutoSelectCode() %>' context="javaScript"/>|<carlos:encode value='<%= digSearchModel.getAutoSelectDesc() %>' context="javaScript"/>');
                -->
            </script>
            <% } %>
            </tbody>
        </table>
    </form>
    <p>&nbsp;</p>
    </body>
</html>
