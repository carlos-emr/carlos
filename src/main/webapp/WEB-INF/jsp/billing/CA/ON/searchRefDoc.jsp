<%--

    Copyright (c) 2007 Peter Hutten-Czapski based on OSCAR general requirements
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
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%@ page import="io.github.carlos_emr.carlos.billings.ca.on.data.SearchRefDocViewModel" %>
<%@ page import="io.github.carlos_emr.carlos.billings.ca.on.web.ViewSearchRefDoc2Action" %>
<%@ page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.managers.SecurityInfoManager" %>
<fmt:setBundle basename="oscarResources"/>
<%--
  Defensive model-resolver: ensures the refDocModel attribute is set on the
  request even if this JSP is reached without going through
  ViewSearchRefDoc2Action. Re-runs the _billing r privilege check for parity.
--%>
<%
    if (request.getAttribute("refDocModel") == null) {
        if (session.getAttribute("user") == null) {
            response.sendRedirect(request.getContextPath() + "/logoutPage");
            return;
        }
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null) {
            response.sendRedirect(request.getContextPath() + "/logoutPage");
            return;
        }
        SecurityInfoManager __secMgr;
        try {
            __secMgr = SpringUtils.getBean(SecurityInfoManager.class);
        } catch (RuntimeException __springEx) {
            io.github.carlos_emr.carlos.utility.MiscUtils.getLogger().error(
                    "searchRefDoc.jsp fallback: SecurityInfoManager bean lookup failed", __springEx);
            throw new SecurityException("searchRefDoc.jsp fallback: privilege check unavailable", __springEx);
        }
        if (!__secMgr.hasPrivilege(loggedInInfo, "_billing", "r", null)) {
            throw new SecurityException("searchRefDoc.jsp fallback: missing required sec object (_billing)");
        }
        request.setAttribute("refDocModel", new ViewSearchRefDoc2Action().assembleViewModel(request));
    }
%>


<!DOCTYPE html>
<html>
    <head>
        <base href="<carlos:encode value='${pageContext.request.scheme}' context='htmlAttribute'/>://<carlos:encode value='${pageContext.request.serverName}' context='htmlAttribute'/>:<carlos:encode value='${pageContext.request.serverPort}' context='htmlAttribute'/><carlos:encode value='${pageContext.request.contextPath}' context='htmlAttribute'/>/">
        <title><fmt:message key="encounter.oscarConsultationRequest.ConsultationFormRequest.optChooseSpec"/></title>
        <link href="<carlos:encode value='${pageContext.request.contextPath}' context='htmlAttribute'/>/library/bootstrap/5.3.8/css/bootstrap.min.css" rel="stylesheet"> <!-- Bootstrap 2.3.1 -->
        <link href="<carlos:encode value='${pageContext.request.contextPath}' context='htmlAttribute'/>/library/DataTables/DataTables-1.13.4/css/dataTables.bootstrap5.min.css" rel="stylesheet">
        <link href="<carlos:encode value='${pageContext.request.contextPath}' context='htmlAttribute'/>/library/DataTables/DataTables-1.13.4/css/dataTables.bootstrap5.min.css"
              rel="stylesheet">
        <script src="<carlos:encode value='${pageContext.request.contextPath}' context='htmlAttribute'/>/library/jquery/jquery-3.7.1.min.js"></script>
        <script src="<carlos:encode value='${pageContext.request.contextPath}' context='htmlAttribute'/>/library/DataTables/datatables.min.js"></script>
        <!-- DataTables 1.13.4 -->

        <script>
            <c:if test="${refDocModel.showTypeInData2}">

            function typeInData2(data1, data2) {
                opener.document.forms[<carlos:encode value='${refDocModel.fld1.formIdx}' context='javaScript'/>].elements["<carlos:encode value='${refDocModel.fld1.fieldId}' context='javaScriptBlock'/>"].value = data1;
                opener.document.forms[<carlos:encode value='${refDocModel.fld2.formIdx}' context='javaScript'/>].elements["<carlos:encode value='${refDocModel.fld2.fieldId}' context='javaScriptBlock'/>"].value = data2;
                self.close();
            }

            </c:if>
            <c:if test="${refDocModel.showTypeInData2Param2Only}">

            function typeInData2(data1, data2) {
                opener.document.forms[<carlos:encode value='${refDocModel.fld2.formIdx}' context='javaScript'/>].elements["<carlos:encode value='${refDocModel.fld2.fieldId}' context='javaScriptBlock'/>"].value = data2;
                self.close();
            }

            </c:if>

            function typeInData3(billno, toname, toaddress, tophone, tofax) {
                var fieldsSet = false;
                <c:if test="${not empty refDocModel.fld1.fieldId}">
                opener.document.forms[<carlos:encode value='${refDocModel.fld1.formIdx}' context='javaScript'/>].elements["<carlos:encode value='${refDocModel.fld1.fieldId}' context='javaScriptBlock'/>"].value = billno;
                fieldsSet = true;
                </c:if>
                <c:if test="${not empty refDocModel.fld3.fieldId}">
                opener.document.forms[<carlos:encode value='${refDocModel.fld3.formIdx}' context='javaScript'/>].elements["<carlos:encode value='${refDocModel.fld3.fieldId}' context='javaScriptBlock'/>"].value = toname;
                fieldsSet = true;
                </c:if>
                <c:if test="${not empty refDocModel.fld4.fieldId}">
                opener.document.forms[<carlos:encode value='${refDocModel.fld4.formIdx}' context='javaScript'/>].elements["<carlos:encode value='${refDocModel.fld4.fieldId}' context='javaScriptBlock'/>"].value = toaddress;
                fieldsSet = true;
                </c:if>
                <c:if test="${not empty refDocModel.fld5.fieldId}">
                opener.document.forms[<carlos:encode value='${refDocModel.fld5.formIdx}' context='javaScript'/>].elements["<carlos:encode value='${refDocModel.fld5.fieldId}' context='javaScriptBlock'/>"].value = tophone;
                fieldsSet = true;
                </c:if>
                <c:if test="${not empty refDocModel.fld6.fieldId}">
                opener.document.forms[<carlos:encode value='${refDocModel.fld6.formIdx}' context='javaScript'/>].elements["<carlos:encode value='${refDocModel.fld6.fieldId}' context='javaScriptBlock'/>"].value = tofax;
                fieldsSet = true;
                </c:if>
                if (!fieldsSet) {
                    alert("Error: Unable to transfer referral doctor data to the billing form. Please close this window and try again.");
                    return;
                }
                self.close();
            }
        </script>
        <script>
            jQuery(document).ready(function () {
                jQuery('#tblDocs').DataTable({
                    "lengthMenu": [[10, 25, 50, -1], [10, 25, 50, "<fmt:message key="encounter.LeftNavBar.AllLabs"/>"]],
                    "order": [],
                    "language": {
                        "url": "<carlos:encode value='${pageContext.request.contextPath}' context='javaScriptAttribute'/>/library/DataTables/i18n/<fmt:message key="global.i18n.datatablescode"/>.json"
                    }
                });
            });
        </script>
    </head>
    <body>
    <h3><fmt:message key="encounter.oscarConsultationRequest.ConsultationFormRequest.optChooseSpec"/></h3>&nbsp;<carlos:encode value='${refDocModel.keyword}' context='html'/>&nbsp;<input
            type="button" class="btn-link" value="<fmt:message key="report.reportindex.formAllProviders"/>"
            onclick="location = location.href.replace(/(\?|\&)(keyword)([^&]*)/, '').replace(/(\?|\&)(submit)([^&]*)/, '');">
    <div class="container-fluid">
        <table style="width:100%" id="tblDocs" class="table table-sm">
            <thead>
            <tr class="title">
                <th><fmt:message key="encounter.oscarConsultationRequest.config.AddSpecialist.referralNo"/></th>
                <th><fmt:message key="encounter.oscarConsultationRequest.config.AddSpecialist.lastName"/></th>
                <th><fmt:message key="encounter.oscarConsultationRequest.config.AddSpecialist.firstName"/></th>
                <th><fmt:message key="encounter.oscarConsultationRequest.config.AddSpecialist.specialistType"/></th>
                <th><fmt:message key="encounter.oscarConsultationRequest.config.AddSpecialist.phone"/></th>
                <th><fmt:message key="encounter.oscarConsultationRequest.config.AddSpecialist.address"/></th>
            </tr>
            </thead>
            <tbody>
            <c:forEach var="sp" items="${refDocModel.specialists}" varStatus="ctr">
                <c:set var="bgColor" value="${ctr.index % 2 == 0 ? '#f9f9f9' : '#ffffff'}"/>
                <tr style="background-color:<carlos:encode value='${bgColor}' context='cssString'/>"
                    onmouseover="this.style.cursor='pointer';this.style.backgroundColor='LightBlue';"
                    onmouseout="this.style.backgroundColor='<carlos:encode value="${bgColor}" context="javaScriptAttribute"/>'"
                    onClick="<carlos:encode value='${sp.onClickHandler}' context='javaScriptAttribute'/>">
                    <td><carlos:encode value='${sp.referralNo}' context='html'/></td>
                    <td><carlos:encode value='${sp.surname}' context='html'/></td>
                    <td><carlos:encode value='${sp.givenName}' context='html'/></td>
                    <td><carlos:encode value='${sp.specialty}' context='html'/></td>
                    <td title="<fmt:message key="encounter.oscarConsultationRequest.config.EditSpecialists.fax"/> <carlos:encode value='${sp.fax}' context='htmlAttribute'/>"><carlos:encode value='${sp.phone}' context='html'/></td>
                    <td style="max-width: 200px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;"
                        title="<carlos:encode value='${sp.address}' context='htmlAttribute'/>"><carlos:encode value='${sp.address}' context='html'/></td>
                </tr>
            </c:forEach>
            </tbody>
        </table>
        <br>
        <a class="btn btn-secondary"
           href="<carlos:encode value='${pageContext.request.contextPath}' context='htmlAttribute'/>/encounter/oscarConsultationRequest/config/ViewEditSpecialists"><fmt:message key="encounter.oscarConsultationRequest.config.EditSpecialists.title"/></a>

    </div>
    </body>
</html>
