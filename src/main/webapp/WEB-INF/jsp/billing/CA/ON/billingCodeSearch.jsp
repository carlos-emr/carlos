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
  Purpose: Supports billingCodeSearch in the Ontario billing workflow.
  Expected request model data includes: codeSearchModel.
  Keep request setup in the paired action and use CARLOS encoding helpers
  for dynamic output rendered by the page.
--%>
<%@page errorPage="/WEB-INF/jsp/error/errorpage.jsp" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%
    // ViewBillingCodeSearch2Action enforces _billing r and assembles the
    // view model with the BillingServiceDao lookup + nameF validation
    // the JSP body used to perform inline. Defensive fallback: empty stub
    // if forwarded here without the canonical action.
    %>
<html>
<head>
    <script type="text/javascript" src="${pageContext.request.contextPath}/js/global.js"></script>
    <title>Service Code Search</title>
    <script LANGUAGE="JavaScript">
        <!--
        function CodeAttach(File0) {
            <c:choose>
                <c:when test="${codeSearchModel.hasNameF}">
            // nameFSafe is validated against [a-zA-Z_][a-zA-Z0-9_.]* in the
            // assembler, so it's safe to splice as a JS identifier path.
            self.opener.${codeSearchModel.nameFSafe} = File0;
                </c:when>
                <c:otherwise>
            self.opener.document.serviceform.xml_other1.value = File0;
            self.opener.document.serviceform.xml_other2.value = "";
            self.opener.document.serviceform.xml_other3.value = "";
                </c:otherwise>
            </c:choose>
            self.close();
        }

        -->
    </script>

</head>

<body bgcolor="#FFFFFF" text="#000000" topmargin="0" leftmargin="0"
      rightmargin="0">
<table border="0" cellspacing="0" cellpadding="0" width="100%">
    <tr bgcolor="#486ebd">
        <th align=CENTER NOWRAP bgcolor="#CCCCFF"><font face="Helvetica"
                                                        color="#000000">Service Code Search </font><font
                face="Arial, Helvetica, sans-serif" color="#FF0000">(Maximum 3
            selections)</font></th>
    </tr>
</table>
<form name="servicecode" id="servicecode" method="post"
      action="${pageContext.request.contextPath}/billing/CA/ON/BillingCodeUpdate">
    <table width="600" border="1">
        <tr bgcolor="#CCCCFF">
            <td width="12%"><b><font face="Arial, Helvetica, sans-serif"
                                     size="2">Code</font></b></td>
            <td width="88%"><b><font face="Arial, Helvetica, sans-serif"
                                     size="2">Description</font></b></td>
        </tr>

        <c:forEach var="__row" items="${codeSearchModel.rows}" varStatus="__rowStatus">
            <c:set var="__color" value="${__rowStatus.index % 2 == 0 ? '#FFFFFF' : '#EEEEFF'}"/>
        <tr bgcolor="<carlos:encode value='${__color}' context='htmlAttribute'/>">
            <td width="12%"><font face="Arial, Helvetica, sans-serif"
                                  size="2"><input type="checkbox" name="code_<carlos:encode value='${__row.code}' context='htmlAttribute'/>" <c:if test="${__row.preChecked}">checked</c:if>>
                <carlos:encode value="${__row.code}" context="html"/>
            </font></td>
            <td width="88%"><font face="Arial, Helvetica, sans-serif"
                                  size="2"><input type="hidden" name="codedesc_<carlos:encode value='${__row.code}' context='htmlAttribute'/>"
                                                  value="<carlos:encode value='${__row.description}' context='htmlAttribute'/>"><input type="text" name="<carlos:encode value='${__row.code}' context='htmlAttribute'/>"
                                                                                value="<carlos:encode value='${__row.description}' context='htmlAttribute'/>" size="50"><input
                    type="submit"
                    name="update" value="update <carlos:encode value='${__row.code}' context='htmlAttribute'/>"></font></td>
        </tr>
        </c:forEach>

        <c:if test="${codeSearchModel.noMatch}">
        <tr>
            <td colspan="2"><font face="Arial, Helvetica, sans-serif"
                                  size="2">No match found.</font></td>
        </tr>
        </c:if>

        <c:if test="${codeSearchModel.autoSelect}">
        <script LANGUAGE="JavaScript">
            <!--
            CodeAttach('<carlos:encode value="${codeSearchModel.autoSelectCode}" context="javaScriptBlock"/>');
            -->
        </script>
        </c:if>
    </table>
    <input type="submit" name="update" value="Confirm"><input
        type="button" name="cancel" value="Cancel"
        onclick="javascript:window.close()"> <c:if test="${codeSearchModel.hasNameFRaw}"><input type="hidden" name="nameF" value="<carlos:encode value='${codeSearchModel.nameFRaw}' context='htmlAttribute'/>"/></c:if>
</form>
</body>
</html>
