<%--

    Copyright (c) 2012- Centre de Medecine Integree

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

    This software was written for
    Centre de Medecine Integree, Saint-Laurent, Quebec, Canada to be provided
    as part of the OSCAR McMaster EMR System


    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

--%>

<%@page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.UserProperty" %>
<%@ page import="io.github.carlos_emr.carlos.documentManager.EDocUtil" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="org.owasp.encoder.Encode" %>

<%
    String curProvider_no = (String) session.getAttribute("user");
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    ArrayList docTypes = EDocUtil.getDoctypes("demographic");
    UserPropertyDAO userPropertyDAO = (UserPropertyDAO) SpringUtils.getBean(UserPropertyDAO.class);
    UserProperty uProp = userPropertyDAO.getProp(curProvider_no, UserProperty.DOCUMENT_DESCRIPTION_TEMPLATE);
    Boolean clinicDefault = true;

    if (uProp != null && uProp.getValue().equals(UserProperty.USER)) {
        clinicDefault = false;
    }
%>

<!DOCTYPE html>
<html lang="en">
<head>
    <%@ include file="/includes/global-head.jspf" %>
    <title><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.setDocumentDescriptionTemplate.title"/></title>

    <script>
        var useDocumentDescriptionTemplateType;

        function adddocDescription() {
            if (document.docDescriptionForm.docDescription.value.length > 0 && document.docDescriptionForm.docDescriptionShortcut.value.length > 0) {
                var docType = $('#docType').val();
                var docDescription = document.docDescriptionForm.docDescription.value;
                var docShortcut = document.docDescriptionForm.docDescriptionShortcut.value;
                var url = "<%=request.getContextPath()%>/DocumentDescriptionTemplate.do";
                var providerNo = document.docDescriptionForm.providerNo.value;
                $.post(url, {
                    method: 'addDocumentDescription',
                    description: docDescription,
                    shortcut: docShortcut,
                    doctype: docType,
                    providerNo: providerNo
                }, function () {
                    getDocumentDescriptionTemplateFromSelectedDocType();
                });
            } else {
                alert("<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.setDocumentDescriptionTemplate.DescriptionCannotBeEmpty"/>");
            }
        }

        function updatedocDescription() {
            if (document.docDescriptionForm.docDescription.value.length > 0 && document.docDescriptionForm.docDescriptionShortcut.value.length > 0) {
                var id = document.docDescriptionForm.descriptionId.value;
                var docType = $('#docType').val();
                var docDescription = document.docDescriptionForm.docDescription.value;
                var docShortcut = document.docDescriptionForm.docDescriptionShortcut.value;
                var providerNo = document.docDescriptionForm.providerNo.value;
                var url = "<%=request.getContextPath()%>/DocumentDescriptionTemplate.do";
                $.post(url, {
                    method: 'updateDocumentDescription',
                    description: docDescription,
                    shortcut: docShortcut,
                    doctype: docType,
                    id: id,
                    providerNo: providerNo
                }, function () {
                    getDocumentDescriptionTemplateFromSelectedDocType();
                });
            } else {
                alert("<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.setDocumentDescriptionTemplate.DescriptionCannotBeEmpty"/>");
            }
        }

        function deletedocDescription() {
            if (document.docDescriptionForm.docDescription.value.length > 0 && document.docDescriptionForm.docDescriptionShortcut.value.length > 0) {
                var id = document.docDescriptionForm.descriptionId.value;
                var url = "<%=request.getContextPath()%>/DocumentDescriptionTemplate.do";
                $.post(url, {
                    method: 'deleteDocumentDescription',
                    id: id
                }, function () {
                    getDocumentDescriptionTemplateFromSelectedDocType();
                });
            } else {
                alert("<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.setDocumentDescriptionTemplate.DescriptionCannotBeEmpty"/>");
            }
        }

        function getDocumentDescriptionTemplateFromSelectedDocType() {
            var docType = "";
            var providerNo = document.docDescriptionForm.providerNo.value;

            $('#docDescriptionList').empty();

            if ($('#docType')[0].selectedIndex > 0) {
                docType = $('#docType').val();
                $('#tblDesc').css('visibility', 'visible');
            } else {
                $('#tblDesc').css('visibility', 'hidden');
                document.docDescriptionForm.addDescription.style.visibility = 'hidden';
                document.docDescriptionForm.updateDescription.style.visibility = 'hidden';
                document.docDescriptionForm.deleteDescription.style.visibility = 'hidden';
                document.docDescriptionForm.descriptionId.value = "";
                document.docDescriptionForm.docDescription.value = "";
                document.docDescriptionForm.docDescriptionShortcut.value = "";
                return;
            }

            var url = "<%=request.getContextPath()%>/DocumentDescriptionTemplate.do";
            $.post(url, {
                method: 'getDocumentDescriptionFromDocType',
                doctype: docType,
                providerNo: providerNo,
                useDocumentDescriptionTemplateType: useDocumentDescriptionTemplateType
            }, function (data) {
                var json = typeof data === 'string' ? JSON.parse(data) : data;

                if (json != null) {
                    var mySelect = $('<select id="docDescList" class="form-select form-select-sm"></select>');
                    mySelect.on('change', getDescriptionAndShortcutFromSelectedList);
                    mySelect.append('<option value=""></option>');

                    for (var i = 0; i < json.documentDescriptionTemplate.length; i++) {
                        var t = json.documentDescriptionTemplate[i];
                        mySelect.append($('<option></option>').val(t.id).text("(" + t.descriptionShortcut + ")      " + t.description));
                    }
                    $('#docDescriptionList').append(mySelect);
                    getDescriptionAndShortcutFromSelectedList();
                }
            });
        }

        function getDescriptionAndShortcutFromSelectedList() {
            if ($('#docDescList')[0].selectedIndex <= 0) {
                document.docDescriptionForm.addDescription.style.visibility = 'visible';
                document.docDescriptionForm.updateDescription.style.visibility = 'hidden';
                document.docDescriptionForm.deleteDescription.style.visibility = 'hidden';
                document.docDescriptionForm.descriptionId.value = "";
                document.docDescriptionForm.docDescription.value = "";
                document.docDescriptionForm.docDescriptionShortcut.value = "";
            } else {
                var id = $('#docDescList').val();
                var url = "<%=request.getContextPath()%>/DocumentDescriptionTemplate.do";
                $.post(url, {
                    method: 'getDocumentDescriptionFromId',
                    id: id
                }, function (data) {
                    var json = typeof data === 'string' ? JSON.parse(data) : data;
                    if (json != null) {
                        document.docDescriptionForm.addDescription.style.visibility = 'hidden';
                        document.docDescriptionForm.updateDescription.style.visibility = 'visible';
                        document.docDescriptionForm.deleteDescription.style.visibility = 'visible';
                        document.docDescriptionForm.descriptionId.value = json.documentDescriptionTemplate.id;
                        document.docDescriptionForm.docDescription.value = json.documentDescriptionTemplate.description;
                        document.docDescriptionForm.docDescriptionShortcut.value = json.documentDescriptionTemplate.descriptionShortcut;
                    }
                });
            }
        }

        function checkClinicDefault() {
            if ($('#useclinicdefault').is(':checked') && document.docDescriptionForm.providerNo.value != "null") {
                $('#docTypeTable').css('visibility', 'hidden');
                $('#tblDesc').css('visibility', 'hidden');
                document.docDescriptionForm.updateDescription.style.visibility = 'hidden';
                document.docDescriptionForm.deleteDescription.style.visibility = 'hidden';
                document.docDescriptionForm.addDescription.style.visibility = 'hidden';
                var url = "<%=request.getContextPath()%>/DocumentDescriptionTemplate.do";
                $.post(url, {
                    method: 'saveDocumentDescriptionTemplatePreference',
                    defaultShortcut: '<%=UserProperty.CLINIC%>'
                });
            } else {
                useDocumentDescriptionTemplateType = document.docDescriptionForm.providerNo.value != "null" ? "<%=UserProperty.USER%>" : "<%=UserProperty.CLINIC%>";
                var url = "<%=request.getContextPath()%>/DocumentDescriptionTemplate.do";
                $.post(url, {
                    method: 'saveDocumentDescriptionTemplatePreference',
                    defaultShortcut: useDocumentDescriptionTemplateType
                });
                $('#docTypeTable').css('visibility', 'visible');
                $('#docType')[0].selectedIndex = -1;
                $('#tblDesc').css('visibility', 'hidden');
                getDocumentDescriptionTemplateFromSelectedDocType();
            }
        }
    </script>
</head>

<body onload="checkClinicDefault()">
<%
    String providerNo = curProvider_no;
    if (request.getParameter("setDefault") != null && request.getParameter("setDefault").equals("true")) {
        providerNo = null;
    }
%>
<div class="container">

    <div class="page-header-bar">
        <h4 class="page-header-title">
            <i class="fas fa-file-alt page-header-icon"></i>&nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.setDocumentDescriptionTemplate.title"/>
        </h4>
    </div>

    <form method="post" name="docDescriptionForm" action="displayDocumentDescriptionTemplate.jsp" class="mt-3">

        <div id="usefault" class="form-check mb-3" style="<%=providerNo==null? "visibility:hidden" : ""%>">
            <input type="checkbox" class="form-check-input" name="useclinicdefault" <%=clinicDefault ? "checked" : ""%>
                   id="useclinicdefault" onclick="checkClinicDefault()">
            <label class="form-check-label" for="useclinicdefault">
                <fmt:setBundle basename="oscarResources"/><fmt:message key="provider.setDocumentDescriptionTemplate.useClinicDefault"/>
            </label>
        </div>

        <% if (providerNo == null) { %>
        <p class="text-muted"><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.setDocumentDescriptionTemplate.setClinicDefault"/></p>
        <% } %>

        <table id="docTypeTable" class="mb-3">
            <tr>
                <td class="pe-2 fw-bold"><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.setDocumentDescriptionTemplate.Type"/>:</td>
                <td>
                    <select name="docType" id="docType" onchange="getDocumentDescriptionTemplateFromSelectedDocType()"
                            class="form-select form-select-sm" style="width:auto;">
                        <option value=""><fmt:setBundle basename="oscarResources"/><fmt:message key="dms.incomingDocs.selectType"/></option>
                        <%
                            for (int j = 0; j < docTypes.size(); j++) {
                                String docType = (String) docTypes.get(j);
                        %>
                        <option value="<%=Encode.forHtmlAttribute(docType)%>"><%=Encode.forHtml(docType)%></option>
                        <% } %>
                    </select>
                </td>
            </tr>
            <tr>
                <td class="pe-2 fw-bold pt-2">
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="provider.setDocumentDescriptionTemplate.Description"/>:
                </td>
                <td class="pt-2">
                    <div id="docDescriptionList"></div>
                </td>
            </tr>
        </table>

        <table style="visibility:hidden" id="tblDesc" class="mb-3">
            <tr>
                <th class="pe-3"><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.setDocumentDescriptionTemplate.DescriptionShortcut"/></th>
                <th><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.setDocumentDescriptionTemplate.Description"/></th>
            </tr>
            <tr>
                <td>
                    <input type="hidden" name="providerNo" value="<%=(providerNo==null?"null":Encode.forHtmlAttribute(providerNo))%>">
                    <input type="hidden" name="descriptionId">
                    <input name="docDescriptionShortcut" maxlength="20" size="20" value=""
                           class="form-control form-control-sm">
                </td>
                <td>
                    <input name="docDescription" maxlength="255" size="60" value=""
                           class="form-control form-control-sm">
                </td>
            </tr>
            <tr>
                <td colspan="2" class="pt-2">
                    <div class="d-flex gap-2">
                        <input type="button" class="btn btn-primary btn-sm"
                               value="<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.setDocumentDescriptionTemplate.Add"/>"
                               id="addDescription" onclick="adddocDescription()">
                        <input type="button" class="btn btn-primary btn-sm"
                               value="<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.setDocumentDescriptionTemplate.Update"/>"
                               id="updateDescription" onclick="updatedocDescription()">
                        <input type="button" class="btn btn-outline-danger btn-sm"
                               value="<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.setDocumentDescriptionTemplate.Delete"/>"
                               id="deleteDescription" onclick="deletedocDescription()">
                    </div>
                </td>
            </tr>
        </table>
    </form>

</div>
</body>
</html>
