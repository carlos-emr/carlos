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
  Purpose: Supports billingResearchCodeUpdate in the Ontario billing workflow.
  Keep request setup in the paired action and use CARLOS encoding helpers
  for dynamic output rendered by the page.
--%>
<%--
    billingResearchCodeUpdate.jsp (view) - Ontario billing research-code popup.
    Receives `code_*` checkboxes from billingONShortcut.jsp, has the
    BillingResearchCodeUpdate2Action upstream collect them into three
    request-scoped slots, then injects those values back into the opener's
    serviceform via a small JS handler. _billing w + POST-only is enforced
    by the action gate.
    @since 2006
--%>
<%@ page errorPage="/WEB-INF/jsp/error/errorpage.jsp" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>

<html>
<head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
    <script type="text/javascript" src="${pageContext.request.contextPath}/js/global.js"></script>
    <title>Billing Summary</title>
    <script LANGUAGE="JavaScript">
        <!--
        function CodeAttach(File0, File1, File2) {

            self.close();
            self.opener.document.serviceform.xml_research1.value = File0;
            self.opener.document.serviceform.xml_research2.value = File1;
            self.opener.document.serviceform.xml_research3.value = File2;
        }

        -->
    </script>

</head>
<body>
<c:choose>
    <c:when test="${researchCodeCount == 0}">
        <p>No input selected</p>
        <input type="button" name="back" value="back"
               onClick="javascript:history.go(-1);return false;">
    </c:when>
    <c:otherwise>
        <script LANGUAGE="JavaScript">
            <!--
            CodeAttach('<carlos:encode value="${researchCode0}" context="javaScript"/>', '<carlos:encode value="${researchCode1}" context="javaScript"/>', '<carlos:encode value="${researchCode2}" context="javaScript"/>');
            -->

        </script>
    </c:otherwise>
</c:choose>
</body>
</html>
