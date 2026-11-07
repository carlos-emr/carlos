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
<%--
    Referral directory: search and edit specialists, select a session checklist,
    and print complete label batches (up to the action's configured batch limit).
    Parameters: nameQuery and referral search filters are handled by the backing action.
    Session: billingReferralAdminCheckList stores the selected specialists.
    @since 2026-09-19
--%>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
"http://www.w3.org/TR/html4/loose.dtd">
<%@ include file="/taglibs.jsp" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>

<%@ taglib uri="carlos" prefix="carlos" %>
<%
    if (session.getAttribute("userrole") == null) response.sendRedirect(request.getContextPath() + "/logoutPage");
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
%>
<security:oscarSec roleName="<%=roleName$%>"
                   objectName="_admin,_admin.misc" rights="r" reverse="<%=true%>">
    <%response.sendRedirect(request.getContextPath() + "/logoutPage");%>
</security:oscarSec>

<%@page import="io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist" %>
<%@page import="io.github.carlos_emr.carlos.commn.web.PrintReferralLabel2Action" %>
<%@page import="java.util.*" %>
<%@ page import="io.github.carlos_emr.carlos.util.StringUtils" %>

<%
    String searchBy = "searchByName";
    if (request.getAttribute("searchBy") != null) {
        searchBy = (String) request.getAttribute("searchBy");
    }

    String name = (String) request.getAttribute("name");
    String specialty = (String) request.getAttribute("specialty");
    String addressQ = (String) request.getAttribute("address");
    Boolean checked = (Boolean) request.getAttribute("showHidden");
%>
<html>
    <head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title><fmt:message key="admin.admin.billingreferralAdmin"/></title>
        <link rel="stylesheet" type="text/css" href="<%= request.getContextPath() %>/share/css/OscarStandardLayout.css">

        <script type="text/javascript" language="JavaScript" src="<%= request.getContextPath() %>/share/javascript/Oscar.js"></script>

        <script src="<%=request.getContextPath() %>/library/jquery/jquery-3.7.1.min.js" type="text/javascript"></script>
        <script src="<%=request.getContextPath() %>/library/jquery/jquery-compat.js"></script>

        <script>

            function popupOscarRx(vheight, vwidth, varpage) {
                var page = varpage;
                windowprops = "height=" + vheight + ",width=" + vwidth + ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes,screenX=0,screenY=0,top=0,left=0";
                var popup = window.open(varpage, "ps_add", windowprops);
                if (popup != null) {
                    if (popup.opener == null) {
                        popup.opener = self;
                    }
                    popup.focus();
                }
            }


            function openAddSpecialist() {
                popupOscarRx(625, 1024, '<%= request.getContextPath() %>/encounter/oscarConsultationRequest/config/ViewAddSpecialist');
                return false;
            }

            function openEditSpecialist(specId) {
                popupOscarRx(625, 1024, '<%=request.getContextPath()%>/encounter/EditSpecialists?specId=' + specId);
            }

            function checkUncheck(referralId) {
                // Exact name: every page of the list is in the document, so a prefix match on
                // checked_5 would also find checked_50 (hidden, disabled, unchecked) on another page.
                var checked = $("input[name='checked_" + referralId + "']").prop("checked");

                $.post("<%= request.getContextPath() %>/admin/ManageBillingReferral",
                    { method: "modifyBatch", id: referralId, checked: checked },
                    function (data, textStatus) {
                        updateCheckedList(data);
                    }, "json");
            }

            function clearCheckedLabels(referralId) {
                $.post("<%= request.getContextPath() %>/admin/ManageBillingReferral",
                    { method: "modifyBatch", clear: true },
                    function (data, textStatus) {
                        updateCheckedList(data);
                    }, "json");
            }

            function printAllCheckedLabels() {
                $("#checked_items_tbl tbody tr").remove();
                $("#checked_items_tbl tbody").append("<tr><td>Processing. Refresh to get updated list</td></tr>");
                location.href = '<%=request.getContextPath() %>/printReferralLabelAction?useCheckList=true';
            }

            function updateCheckedList(data) {
                $("#checked_items_tbl tbody tr").remove();

                if (data == null || data.length == 0) {
                    $("#checked_items_tbl tbody").append("<tr><td>-None-</td></tr>");
                }
                for (var x = 0; x < data.length; x++) {
                    $("#checked_items_tbl tbody").append("<tr><td>" + data[x].formattedName + "</td></tr>");
                }
            }

            function clearMe() {
                document.getElementById('nameQuery').value = '';
                document.getElementById('specialtyQuery').value = '';
                document.getElementById('showHidden').checked = false;
                document.getElementById('addressQuery').value = '';
            }
        </script>
        <link href="${pageContext.request.contextPath}/css/displaytag.css" rel="stylesheet"></link>
    <%@ include file="/WEB-INF/jsp/common/carlosTableAssets.jspf" %>
</head>

    <body vlink="#0000FF" class="BodyStyle">

    <table class="MainTable">
        <tr class="MainTableTopRow">
            <td class="MainTableTopRowLeftColumn"><fmt:message key="global.admin"/></td>
            <td class="MainTableTopRowRightColumn">
                <table class="TopStatusBar" style="width: 100%;">
                    <tr>
                        <td><fmt:message key="admin.admin.billingreferralAdmin"/></td>
                    </tr>
                </table>
            </td>
        </tr>
        <tr>
            <td class="MainTableLeftColumn" valign="top" width="160px;">&nbsp;
            </td>
            <td class="MainTableRightColumn" valign="top">


                <form action="<%= request.getContextPath() %>/admin/ManageBillingReferral">
                    <input type="hidden" name="method" value="advancedSearch"/>
                    <input type="text" name="nameQuery" id="nameQuery" placeholder="<fmt:message key='admin.billingreferralAdmin.placeholder.nameOrReferralId'/>"
                           value="<carlos:encode value='<%= name != null ? name : "" %>' context="htmlAttribute"/>">
                    &nbsp;
                    <input type="text" name="specialtyQuery" id="specialtyQuery" placeholder="<fmt:message key='admin.billingreferralAdmin.placeholder.specialty'/>"
                           value="<carlos:encode value='<%= specialty != null ? specialty : "" %>' context="htmlAttribute"/>">
                    &nbsp;
                    <input type="text" name="addressQuery" id="addressQuery" placeholder="<fmt:message key='admin.billingreferralAdmin.placeholder.address'/>"
                           value="<carlos:encode value='<%= addressQ != null ? addressQ : "" %>' context="htmlAttribute"/>">
                    &nbsp;
                    <fmt:message key="admin.billingreferralAdmin.label.includeHidden"/>:
                    <input type="checkbox" name="showHidden"
                           id="showHidden" <%=(checked != null && checked) ? " checked=\"checked\" " : "" %> />

                    <input type="submit" style="border:1px solid #666666;" value="<fmt:message key='admin.billingreferralAdmin.btnSearch'/>" />
                    <input type="submit" style="border:1px solid #666666;" onclick="clearMe()" value="<fmt:message key='admin.billingreferralAdmin.btnClear'/>" />
                    <input type="submit" style="border:1px solid #666666;"
                                   onclick="return openAddSpecialist()" value="<fmt:message key='admin.billingreferralAdmin.btnAdd'/>" />
                </form>
                <br/>
                <%
                    if (request.getAttribute("referrals") == null) {
                %>
                <h3 style="color:red"><fmt:message key="admin.billingreferralAdmin.msgNoResults"/></h3>
                <%
                } else {
                %>
                <c:if test="${not empty referrals}">
                <table class="its" style="border:1px solid #666666; width:99%;margin-top:2px;" id="referral" data-carlos-table data-page-size="15" data-default-sort="0" data-default-order="ascending">
<thead><tr>


                    <th scope="col"></th>
                    <th scope="col"></th>
                    <th scope="col"><fmt:message key='admin.billingreferralAdmin.col.firstName'/></th>
                    <th scope="col"><fmt:message key='admin.billingreferralAdmin.col.lastName'/></th>
                    <th scope="col"><fmt:message key='admin.billingreferralAdmin.col.specialty'/></th>
                    <th scope="col"><fmt:message key='admin.billingreferralAdmin.col.address'/></th>
                    <th scope="col"><fmt:message key='admin.billingreferralAdmin.col.phone'/></th>
                    <th scope="col"><fmt:message key='admin.billingreferralAdmin.col.fax'/></th>
                    <th scope="col"><fmt:message key='admin.billingreferralAdmin.col.label'/></th>
                </tr></thead>
<tbody><c:forEach var="referral" items="${referrals}" varStatus="tableRowStatus"><tr class="${tableRowStatus.count % 2 == 0 ? 'even' : 'odd'}">
                    <%
                        ProfessionalSpecialist ps = (ProfessionalSpecialist) pageContext.getAttribute("referral");

                        String linkName = ps.getReferralNo();
                        if (StringUtils.isNullOrEmpty(ps.getReferralNo())) {
                            linkName = "N/A";
                        }
                    %>

                    <td><input type="checkbox" id="referral-select-${carlos:forHtmlAttribute(referral.id)}"
                               name="checked_${carlos:forHtmlAttribute(referral.id)}"
                               onChange="checkUncheck('${carlos:forJavaScriptAttribute(referral.id)}')"/>
                        <label class="table-selection-label" for="referral-select-${carlos:forHtmlAttribute(referral.id)}"><fmt:message key="dms.incomingDocs.select"/> ${carlos:forHtml(referral.firstName)} ${carlos:forHtml(referral.lastName)}</label></td>
                    <td><a href="javascript:void(0)"
                                       onclick="openEditSpecialist('${carlos:forJavaScriptAttribute(referral.id)}')"><carlos:encode value='<%= linkName %>'/>
                    </a></td>
                    <td data-sort-value="${carlos:forHtmlAttribute(carlos:tableSortValue(referral.firstName))}" data-sort-type="${carlos:tableSortType(referral.firstName)}">${carlos:forHtml(referral.firstName)}</td>
                    <td data-sort-value="${carlos:forHtmlAttribute(carlos:tableSortValue(referral.lastName))}" data-sort-type="${carlos:tableSortType(referral.lastName)}">${carlos:forHtml(referral.lastName)}</td>
                    <td data-sort-value="${carlos:forHtmlAttribute(carlos:tableSortValue(referral.specialtyType))}" data-sort-type="${carlos:tableSortType(referral.specialtyType)}">${carlos:forHtml(referral.specialtyType)}</td>
                    <td data-sort-value="${carlos:forHtmlAttribute(carlos:tableSortValue(referral.streetAddress))}" data-sort-type="${carlos:tableSortType(referral.streetAddress)}">${carlos:forHtml(referral.streetAddress)}</td>
                    <td data-sort-value="${carlos:forHtmlAttribute(carlos:tableSortValue(referral.phoneNumber))}" data-sort-type="${carlos:tableSortType(referral.phoneNumber)}">${carlos:forHtml(referral.phoneNumber)}</td>
                    <td data-sort-value="${carlos:forHtmlAttribute(carlos:tableSortValue(referral.faxNumber))}" data-sort-type="${carlos:tableSortType(referral.faxNumber)}">${carlos:forHtml(referral.faxNumber)}</td>
                    <td><c:url var="tableColumnUrl" value="/printReferralLabelAction"><c:param name="billingreferralNo" value="${referral.id}"/></c:url><a href="${carlos:forHtmlAttribute(tableColumnUrl)}"><fmt:message key="admin.billingreferralAdmin.label.link"/></a></td>
                </tr></c:forEach></tbody>
</table>
                </c:if>
<c:if test="${empty referrals}"><fmt:message key="table.msg.emptyList"/></c:if>
            </td>
        </tr>
        <tr>
            <td class="MainTableBottomRowLeftColumn">&nbsp;</td>
            <td class="MainTableBottomRowRightColumn">
                <div id="checked_items">
                    <br/>
                    <h3><fmt:message key="admin.billingreferralAdmin.heading.selectedSpecialists"/></h3>
                    <br/>
                    <table id="checked_items_tbl">
                        <tbody>
                        <%
                            List<ProfessionalSpecialist> checkedSpecs = (List<ProfessionalSpecialist>) session.getAttribute("billingReferralAdminCheckList");
                            if (checkedSpecs != null && checkedSpecs.size() > 0) {
                                for (ProfessionalSpecialist ps : checkedSpecs) {
                        %>
                        <tr>
                            <td><carlos:encode value='<%= ps.getFormattedName() %>' context="html"/>
                            </td>
                        </tr>
                        <%
                            }
                        } else {
                        %>
                        <tr>
                            <td><fmt:message key="admin.billingreferralAdmin.msgNone"/></td>
                        </tr>
                        <% } %>
                        </tbody>
                    </table>
                </div>
                <br/>
                <p><fmt:message key="admin.billingreferralAdmin.batchLimit"><fmt:param value="<%= PrintReferralLabel2Action.MAX_LABELS_PER_BATCH %>"/></fmt:message></p>
                <input type="button" value="<fmt:message key='admin.billingreferralAdmin.btnGenerateLabels'/>" onClick="printAllCheckedLabels()"/>
                <input type="button" value="<fmt:message key='admin.billingreferralAdmin.btnClearList'/>" onClick="clearCheckedLabels()"/>

            </td>
        </tr>
        <tr>
            <td class="MainTableBottomRowLeftColumn">&nbsp;</td>

            <td class="MainTableBottomRowRightColumn">&nbsp;</td>
        </tr>
    </table>
    <% } %>
</html>
