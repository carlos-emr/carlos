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
  Purpose: Supports billingCodeUpdate in the Ontario billing workflow.
  Expected request model data includes: codeUpdateModel.
  Keep request setup in the paired action and use CARLOS encoding helpers
  for dynamic output rendered by the page.
--%>
<%@page errorPage="/WEB-INF/jsp/error/errorpage.jsp" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%
    // BillingCodeUpdate2Action enforces _billing w + POST and assembles the
    // view model. The assembler does either:
    //   - Confirm-mode: collects the code_* checkbox params into selected0/1/2
    //   - Update-mode:  persists a single BillingService description edit
    // Defensive fallback: if a caller forwards here without going through the
    // action, render an empty model — never re-trigger the persist mutation.
    %>
<html>
<head>
    <script type="text/javascript" src="${pageContext.request.contextPath}/js/global.js"></script>
    <title>Billing Summary</title>
    <script LANGUAGE="JavaScript">
        <!--
        function CodeAttach(File0, File1, File2) {
            <c:choose>
                <c:when test="${codeUpdateModel.hasNameF}">
            // nameFSafe is validated against [a-zA-Z_][a-zA-Z0-9_.]* in the
            // assembler, so it's safe to splice as a JS identifier path.
            self.opener.${codeUpdateModel.nameFSafe} = File0;
                </c:when>
                <c:otherwise>
            self.opener.document.serviceform.xml_other1.value = File0;
            self.opener.document.serviceform.xml_other2.value = File1;
            self.opener.document.serviceform.xml_other3.value = File2;
                </c:otherwise>
            </c:choose>
            self.close();
        }

        -->
    </script>

</head>
<body>
<c:choose>
    <c:when test="${codeUpdateModel.confirmMode}">
        <c:choose>
            <c:when test="${codeUpdateModel.noSelection}">
<p>No input selected</p>
<input type="button" name="back" value="back"
       onClick="javascript:history.go(-1);return false;">
            </c:when>
            <c:otherwise>
<script LANGUAGE="JavaScript">
    <!--
    CodeAttach('<carlos:encode value="${codeUpdateModel.selected0}" context="javaScriptBlock"/>', '<carlos:encode value="${codeUpdateModel.selected1}" context="javaScriptBlock"/>', '<carlos:encode value="${codeUpdateModel.selected2}" context="javaScriptBlock"/>');
    -->

</script>
            </c:otherwise>
        </c:choose>
    </c:when>
    <c:otherwise>
<%-- Update mode: assembler already persisted; emit popup-close JS. --%>
<p>
<h1>Successful Addition of a billing Record.</h1>
</p>
<script LANGUAGE="JavaScript">
    history.go(-1);
    return false;
    self.opener.refresh();
</script>
    </c:otherwise>
</c:choose>

</body>
</html>
