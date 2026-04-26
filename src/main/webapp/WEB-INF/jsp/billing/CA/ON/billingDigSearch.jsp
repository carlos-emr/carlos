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
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%@page import="io.github.carlos_emr.carlos.billings.ca.on.data.BillingDigSearchViewModel" %>
<fmt:setBundle basename="oscarResources"/>

<%
    // ViewBillingDigSearch2Action enforces _billing r and assembles the
    // view model with the DiagnosticCodeDao lookups + name2 JS-path
    // parsing the JSP body used to perform inline. Defensive fallback:
    // empty stub if forwarded here without the canonical action.
    if (request.getAttribute("digSearchModel") == null) {
        io.github.carlos_emr.carlos.utility.MiscUtils.getLogger().warn(
                "billingDigSearch.jsp reached without digSearchModel — caller "
              + "should route through billing/CA/ON/ViewBillingDigSearch.");
        request.setAttribute("digSearchModel",
                BillingDigSearchViewModel.builder().build());
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

                <c:choose>
                    <c:when test="${digSearchModel.hasTargetElement}">
                self.opener.document.forms[${digSearchModel.targetFormIdx}].elements["<carlos:encode value='${digSearchModel.targetElement}' context='javaScriptBlock'/>"].value = File2.substring(0, 3);
                    </c:when>
                    <c:when test="${digSearchModel.name2ParseError}">
                alert("Error: Unable to transfer diagnostic code to the billing form. Please close this window and try again.");
                return;
                    </c:when>
                    <c:otherwise>
                self.opener.document.forms[1].xml_diagnostic_detail.value = File2;
                    </c:otherwise>
                </c:choose>
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
    <c:if test="${digSearchModel.name2ParseError}">
    <script>alert("Warning: The diagnostic code field reference could not be parsed. Selecting a code may not work correctly. Please close this window and try again from the billing form.");</script>
    </c:if>
    <table style="width:100%">
        <tr>
            <th style="text-align:center; background-color:silver;"><fmt:message key="billing.billingDigSearch.msgDiagnostic"/><fmt:message key="billing.billingDigSearch.msgMaxSelections"/></th>
        </tr>
    </table>

    <form name="codesearch" id="codesearch" method="post"
          action="${pageContext.request.contextPath}/billing/CA/ON/ViewBillingDigSearch">
        <c:if test="${digSearchModel.showName2Echo}">
        <input type="hidden" name="name2"
               value="<carlos:encode value='${digSearchModel.name2}' context='htmlAttribute'/>"/>
        </c:if>
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
          action="${pageContext.request.contextPath}/billing/CA/ON/BillingDigUpdate">
        <table style="width:800px; margin:auto" class="table-striped table-sm">
            <thead>
            <tr>
                <th style="width:12%"><b><fmt:message key="billing.billingDigSearch.formCode"/></b></th>
                <th style="width:88%"><b><fmt:message key="billing.billingDigSearch.formDescription"/></b></th>
            </tr>
            </thead>
            <tbody>
            <c:forEach var="__row" items="${digSearchModel.rows}">
            <tr>
                <td style="width:12%"><a
                        href="javascript:CodeAttach('<carlos:encode value='${__row.code}' context='javaScriptAttribute'/>|<carlos:encode value='${__row.description}' context='javaScriptAttribute'/>')"><carlos:encode value="${__row.code}" context="html"/>
                </a></td>
                <td style="width:88%"><input type="text" class="form-control" style="margin-bottom: 0px;"
                                             name="<carlos:encode value='${__row.code}' context='htmlAttribute'/>"
                                             value="<carlos:encode value='${__row.description}' context='htmlAttribute'/>">&nbsp;<input type="submit" class="btn btn-secondary"
                                                                                 name="update"
                                                                                 value="<fmt:message key="billing.billingDigSearch.btnUpdate"/> <carlos:encode value='${__row.code}' context='htmlAttribute'/>">
                </td>
            </tr>
            </c:forEach>

            <c:if test="${digSearchModel.noMatch}">
            <tr>
                <td colspan="2"><fmt:message key="billing.billingDigSearch.msgNoMatch"/>.</td>
            </tr>
            </c:if>

            <c:if test="${digSearchModel.autoSelect}">
            <script LANGUAGE="JavaScript">
                <!--
                CodeAttach('<carlos:encode value="${digSearchModel.autoSelectCode}" context="javaScript"/>|<carlos:encode value="${digSearchModel.autoSelectDesc}" context="javaScript"/>');
                -->
            </script>
            </c:if>
            </tbody>
        </table>
    </form>
    <p>&nbsp;</p>
    </body>
</html>
