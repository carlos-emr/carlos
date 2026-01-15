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

--%>

<!-- add by caisi -->
<%@ taglib uri="/WEB-INF/caisi-tag.tld" prefix="caisi" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<!-- add by caisi end<style>* {border:1px solid black;}</style> -->
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>


<%@ page import="ca.openosp.openo.providers.data.*" %>
<%@ page import="ca.openosp.openo.providers.pageUtil.*" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ page import="ca.openosp.openo.providers.data.ProSignatureData" %>

<%
    if (session.getValue("user") == null)
        response.sendRedirect(request.getContextPath() + "/logout.htm");
    String curUser_no, userfirstname, userlastname;
    curUser_no = (String) session.getAttribute("user");

    ProSignatureData sig = new ProSignatureData();
%>
<html>
    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>

        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">
        <link rel="stylesheet" type="text/css"
              href="<%= request.getContextPath() %>/oscarEncounter/encounterStyles.css">

        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.editSignature.title"/></title>

    </head>

    <body class="BodyStyle" vlink="#0000FF">
    <!-- add by caisi -->
    <caisi:isModuleLoad moduleName="caisi">

        <iframe id="hiddenFrame" src="javascript:void(0)" style="display: none"></iframe>
        <script>
            function toggleSig(n) {
                // Function disabled - infirm.do action no longer exists
            }
        </script>

    </caisi:isModuleLoad>
    <!-- add by caisi end-->
    <table class="MainTable" id="scrollNumber1" name="encounterTable">
        <tr class="MainTableTopRow">
            <td class="MainTableTopRowLeftColumn"><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.editSignature.msgPrefs"/></td>
            <td class="MainTableTopRowRightColumn">
                <table class="TopStatusBar">
                    <tr>
                        <td><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.editSignature.msgProviderSignature"/></td>
                        <td>&nbsp;</td>
                        <td style="text-align: right"><a
                                href="javascript:popupStart(300,400,'About.jsp')"><fmt:setBundle basename="oscarResources"/><fmt:message key="global.about"/></a> | <a
                                href="javascript:popupStart(300,400,'License.jsp')"><fmt:setBundle basename="oscarResources"/><fmt:message key="global.license"/></a></td>
                    </tr>
                </table>
            </td>
        </tr>
        <tr>
            <td class="MainTableLeftColumn">&nbsp;</td>
            <td class="MainTableRightColumn"><form action="${pageContext.request.contextPath}/EnterSignature.do" method="post">
                <%
                    if (sig.hasSignature(curUser_no)) {
                %>
                <fmt:setBundle basename="oscarResources"/><fmt:message key="provider.editSignature.msgEdit"/>
                <br>
                <input type="text" name="signature" size="40" value="<%= Encode.forHtmlAttribute(sig.getSignature(curUser_no)) %>" />
                <br>

                <!-- add by caisi -->
                <caisi:isModuleLoad moduleName="caisi">
                    <INPUT TYPE="checkbox"
                            <%= ((Boolean)session.getAttribute("signOnNote")).booleanValue()?"checked":""%>
                           onchange="toggleSig('<%= curUser_no %>')">also sign the signiture in encounter notes
                </caisi:isModuleLoad>
                <!-- add by caisi end-->

                <input type="submit"
                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.editSignature.btnUpdate"/>"/>
                <% } else {%>
                <fmt:setBundle basename="oscarResources"/><fmt:message key="provider.editSignature.msgNew"/>
                <br>
                <input type="checkbox" name="signature" size="40" />
                <br>
                <!-- add by caisi -->
                <caisi:isModuleLoad moduleName="caisi">
                    <INPUT TYPE="checkbox"
                            <%= ((Boolean)session.getAttribute("signOnNote")).booleanValue()?"checked":""%>
                           onchange="toggleSig('<%= curUser_no %>')">also sign the signature in encounter notes
                </caisi:isModuleLoad>
                <!-- add by caisi end-->
                <input type="submit"
                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.editSignature.btnSubmit"/>"/>
                <%}%>
            </form></td>
        </tr>
        <tr>
            <td class="MainTableBottomRowLeftColumn"></td>
            <td class="MainTableBottomRowRightColumn"></td>
        </tr>
    </table>
    </body>
</html>
