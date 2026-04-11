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
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + ","
            + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_admin" rights="w" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_admin");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>
<%@page import="org.owasp.encoder.Encode" %>
<%@page import="io.github.carlos_emr.carlos.web.admin.KeyManagerUIBean" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.PublicKey" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist" %>
<fmt:setBundle basename="oscarResources"/>
<%@include file="/layouts/html_top.jspf" %>

<h2 class="oscarBlueHeader">
    <fmt:message key="admin.keyManager.title"/>
</h2>

<br/>

<fmt:message key="admin.keyManager.btnCreateNewKey" var="btnCreateNewKeyLabel"/>
<input type="button" value="${btnCreateNewKeyLabel}" onclick="document.location='createKey.jsp'"/>

<br/>
<hr/>
<br/>
<div class="oscarBlueForeground"><fmt:message key="admin.keyManager.sectionUploadUrl"/></div>
<%
    String requestUrl = request.getRequestURL().toString();
    String servletPath = request.getServletPath();
    String uploadUrl = requestUrl.substring(0, requestUrl.length() - servletPath.length());
    uploadUrl = uploadUrl + "/lab/newLabUpload.do";
%>
<div style="border:solid grey 1px;word-wrap:break-word;font-size:12px; width:95%"><%=Encode.forHtml(uploadUrl)%>
</div>
<div style="font-size:12px">
    <fmt:message key="admin.keyManager.msgServerNameNote"/>
</div>
<br/>
<hr/>
<br/>
<div class="oscarBlueForeground"><fmt:message key="admin.keyManager.sectionCarlosPublicKey"/></div>
<div style="border:solid grey 1px;word-wrap:break-word;font-size:12px; width:95%"><%=KeyManagerUIBean.getPublicOscarKeyEscaped()%>
</div>
<br/>
<hr/>
<br/>

<script language="javascript">
    var i18n_changesSaved = '<fmt:message key="admin.keyManager.msgChangesSaved"/>';

    function onSelectService() {
        var selectKeyList = document.getElementById("selectKeyList");

        if (selectKeyList.options.length <= 0) return;

        jQuery.getJSON("getPublicKey.json", {id: getSelectListValue(selectKeyList)},
            function (xml) {
                var privateKeyField = document.getElementById("privateKey");
                privateKeyField.innerHTML = xml.base64EncodedPrivateKey;

                var selectProfessionalSpecialistList = document.getElementById("selectProfessionalSpecialistList");
                selectProfessionalSpecialistList.selectedIndex = 0;

                <%-- json anomalie where null is returned as 0 --%>
                if (xml.matchingProfessionalSpecialistId != null && xml.matchingProfessionalSpecialistId != 0) {
                    selectSelectListOption(selectProfessionalSpecialistList, xml.matchingProfessionalSpecialistId);
                }
            }
        );
    }

    function updateMatchingProcessionalSpecialist() {
        var selectKeyList = document.getElementById("selectKeyList");
        var selectProfessionalSpecialistList = document.getElementById("selectProfessionalSpecialistList");
        jQuery.post("updateMatchingProfessionalSpecialist.jsp", {
                serviceName: getSelectListValue(selectKeyList),
                professionalSpecialistId: getSelectListValue(selectProfessionalSpecialistList)
            },
            function (xml) {
                alert(i18n_changesSaved);
            }
        );
    }
</script>
<div class="oscarBlueForeground"><fmt:message key="admin.keyManager.sectionCurrentPublicKeys"/></div>
<table style="border-collapse:collapse; width:95%; table-layout:fixed;word-wrap:break-word;font-size:12px">
    <tr style="border:solid grey 1px">
        <td class="oscarBlueHeader" style="width:13em"><fmt:message key="admin.keyManager.thServiceName"/></td>
        <td>
            <select id="selectKeyList" onchange="onSelectService()">
                <%
                    for (PublicKey publicKey : KeyManagerUIBean.getPublicKeys()) {
                %>
                <option value="<%=KeyManagerUIBean.getSericeNameEscaped(publicKey)%>"><%=KeyManagerUIBean.getSericeDisplayString(publicKey)%>
                </option>
                <%
                    }
                %>
            </select>
            <script language="javascript">
                onSelectService();
            </script>
        </td>
    </tr>
    <tr style="border:solid grey 1px">
        <td class="oscarBlueHeader"><fmt:message key="admin.keyManager.thPrivateServiceKey"/></td>
        <td id="privateKey"></td>
    </tr>
    <tr style="border:solid grey 1px">
        <td class="oscarBlueHeader"><fmt:message key="admin.keyManager.thMatchingSpecialist"/></td>
        <td>
            <select id="selectProfessionalSpecialistList">
                <option value=""><fmt:message key="admin.keyManager.optNone"/></option>
                <%
                    for (ProfessionalSpecialist professionalSpecialist : KeyManagerUIBean.getProfessionalSpecialists()) {
                %>
                <option value="<%=Encode.forHtmlAttribute(String.valueOf(professionalSpecialist.getId()))%>"><%=KeyManagerUIBean.getProfessionalSpecialistDisplayString(professionalSpecialist)%>
                </option>
                <%
                    }
                %>
            </select>
            <fmt:message key="admin.keyManager.btnUpdateMatchingSpecialist" var="btnUpdateMatchingSpecialistLabel"/>
            <input type="button" value="${btnUpdateMatchingSpecialistLabel}"
                   onclick="updateMatchingProcessionalSpecialist()"/>
        </td>
    </tr>
</table>


<%@include file="/layouts/html_bottom.jspf" %>
