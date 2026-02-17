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


    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

--%>

<%@ taglib uri="/WEB-INF/caisi-tag.tld" prefix="caisi" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%@ page import="io.github.carlos_emr.carlos.providers.data.ProSignatureData" %>
<%@ page import="org.owasp.encoder.Encode" %>

<%
    if (session.getValue("user") == null)
        response.sendRedirect(request.getContextPath() + "/logout.htm");
    String curUser_no = (String) session.getAttribute("user");
    ProSignatureData sig = new ProSignatureData();
%>

<!DOCTYPE html>
<html lang="en">
    <head>
        <%@ include file="/includes/global-head.jspf" %>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.editSignature.title"/></title>
    </head>

    <body>

    <caisi:isModuleLoad moduleName="caisi">
        <iframe id="hiddenFrame" src="javascript:void(0)" style="display: none"></iframe>
        <script>
            function toggleSig(n) {
                // Function disabled - infirm.do action no longer exists
            }
        </script>
    </caisi:isModuleLoad>

    <div class="container">

        <div class="page-header-bar">
            <h4 class="page-header-title">
                <i class="fas fa-signature page-header-icon"></i>&nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.editSignature.msgProviderSignature"/>
            </h4>
        </div>

        <div class="mt-3">
            <form action="${pageContext.request.contextPath}/EnterSignature.do" method="post">
                <%
                    if (sig.hasSignature(curUser_no)) {
                %>
                <div class="mb-3">
                    <label for="signature" class="form-label"><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.editSignature.msgEdit"/></label>
                    <input type="text" name="signature" id="signature" class="form-control form-control-sm" style="max-width:400px;"
                           value="<%= Encode.forHtmlAttribute(sig.getSignature(curUser_no)) %>">
                </div>

                <caisi:isModuleLoad moduleName="caisi">
                    <div class="form-check mb-3">
                        <input type="checkbox" class="form-check-input"
                                <%= ((Boolean)session.getAttribute("signOnNote")).booleanValue()?"checked":""%>
                               onchange="toggleSig('<%= Encode.forJavaScriptAttribute(curUser_no) %>')">
                        <label class="form-check-label">also sign the signature in encounter notes</label>
                    </div>
                </caisi:isModuleLoad>

                <input type="submit" class="btn btn-primary btn-sm"
                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.editSignature.btnUpdate"/>">

                <% } else { %>
                <div class="mb-3">
                    <label for="signature" class="form-label"><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.editSignature.msgNew"/></label>
                    <input type="text" name="signature" id="signature" class="form-control form-control-sm" style="max-width:400px;">
                </div>

                <caisi:isModuleLoad moduleName="caisi">
                    <div class="form-check mb-3">
                        <input type="checkbox" class="form-check-input"
                                <%= ((Boolean)session.getAttribute("signOnNote")).booleanValue()?"checked":""%>
                               onchange="toggleSig('<%= Encode.forJavaScriptAttribute(curUser_no) %>')">
                        <label class="form-check-label">also sign the signature in encounter notes</label>
                    </div>
                </caisi:isModuleLoad>

                <input type="submit" class="btn btn-primary btn-sm"
                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.editSignature.btnSubmit"/>">
                <% } %>
            </form>
        </div>

    </div>
    </body>
</html>
