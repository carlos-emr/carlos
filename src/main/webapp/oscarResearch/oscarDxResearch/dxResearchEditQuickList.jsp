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
<%--
    dxResearchEditQuickList.jsp - Edit diagnosis codes in a quick list

    Purpose:
    Three-panel editor for managing items in a quick list. Left panel has code
    entry fields and a code search button. Center panel has Add/Remove buttons.
    Right panel shows current items in the list as a multi-select.

    Submits to dxResearchUpdateQuickList.do which processes add/remove actions
    and redirects back to dxResearchLoadQuickListItems.do to reload this page.

    Request Attributes:
    - quickListName: Name of the quick list being edited
    - codingSystem: Available coding systems (icd9, icd10, etc.)
    - allQuickListItems: Current items in the selected quick list

    @since 2006-01-01 (original OSCAR implementation)
--%>

<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="owasp.encoder.jakarta" prefix="e" %>

<!DOCTYPE html>
<html>
    <head>
        <%@ include file="/includes/global-head.jspf" %>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarResearch.oscarDxResearch.dxResearch.title"/></title>
        <script type="text/javascript">
            function setfocus() {
                window.focus();
                window.resizeTo(700, 500);
                document.forms[0].xml_research1.focus();
                document.forms[0].xml_research1.select();
            }

            var remote = null;

            function rs(n, u, w, h, x) {
                var args = "width=" + w + ",height=" + h + ",resizable=yes,scrollbars=yes,status=0,top=60,left=30";
                remote = window.open(u, n, args);
                if (remote != null) {
                    if (remote.opener == null)
                        remote.opener = self;
                }
                if (x == 1) {
                    return remote;
                }
            }

            var awnd = null;

            /** Opens the code search popup for the selected coding system and current field values. */
            function ResearchScriptAttach() {
                var t0 = encodeURIComponent(document.forms[0].xml_research1.value);
                var t1 = encodeURIComponent(document.forms[0].xml_research2.value);
                var t2 = encodeURIComponent(document.forms[0].xml_research3.value);
                var t3 = encodeURIComponent(document.forms[0].xml_research4.value);
                var t4 = encodeURIComponent(document.forms[0].xml_research5.value);
                var codeType = document.forms[0].selectedCodingSystem.value;
                awnd = rs('att', 'dxResearchCodeSearch.do?codeType=' + codeType + '&xml_research1=' + t0 + '&xml_research2=' + t1 + '&xml_research3=' + t2 + '&xml_research4=' + t3 + '&xml_research5=' + t4 + '&demographicNo=', 600, 600, 1);
                awnd.focus();
            }

            /** Submits the form with the given forward action (add or remove). */
            function submitform(target) {
                document.forms[0].forward.value = target;
                document.forms[0].submit();
            }
        </script>
    </head>

    <body onload="setfocus()">
    <div class="container" style="padding-top:10px;">

        <%-- Page header matching search.jsp / report.jsp pattern --%>
        <div class="page-header-bar">
            <h4 class="page-header-title">
                <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" fill="currentColor" class="page-header-icon" viewBox="0 0 16 16">
                    <path d="M12.146.146a.5.5 0 0 1 .708 0l3 3a.5.5 0 0 1 0 .708l-10 10a.5.5 0 0 1-.168.11l-5 2a.5.5 0 0 1-.65-.65l2-5a.5.5 0 0 1 .11-.168zM11.207 2.5 13.5 4.793 14.793 3.5 12.5 1.207zm1.586 3L10.5 3.207 4 9.707V10h.5a.5.5 0 0 1 .5.5v.5h.5a.5.5 0 0 1 .5.5v.5h.293zm-9.761 5.175-.106.106-1.528 3.821 3.821-1.528.106-.106A.5.5 0 0 1 5 12.5V12h-.5a.5.5 0 0 1-.5-.5V11h-.5a.5.5 0 0 1-.468-.325"/>
                </svg>
                &nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarResearch.oscarDxResearch.dxResearch.msgDxResearch"/>
                — <c:out value="${quickListName}"/>
            </h4>
        </div>

<%
    java.util.List<String> actionErrors = (java.util.List<String>) request.getAttribute("actionErrors");
    if (actionErrors != null && !actionErrors.isEmpty()) {
%>
        <div class="action-errors">
            <ul>
                <% for (String error : actionErrors) { %>
                    <li><%= org.owasp.encoder.Encode.forHtml(error) %></li>
                <% } %>
            </ul>
        </div>
<% } %>

        <form action="${pageContext.request.contextPath}/oscarResearch/oscarDxResearch/dxResearchUpdateQuickList.do" method="post">
            <input type="hidden" name="forward" value="none"/>
            <input type="hidden" name="quickListName" value="<c:out value="${quickListName}"/>"/>

            <div class="d-flex flex-wrap gap-3 mt-3">

                <%-- Left panel: code entry and search --%>
                <div style="flex:1; min-width:200px;">
                    <div class="mb-2">
                        <label class="form-label"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarResearch.oscarDxResearch.codingSystem"/></label>
                        <select class="form-select form-select-sm" name="selectedCodingSystem" id="selectedCodingSystem">
                            <c:forEach var="codingSys" items="${codingSystem.codingSystems}">
                                <option value="${codingSys}">${codingSys}</option>
                            </c:forEach>
                        </select>
                    </div>
                    <input type="text" class="form-control form-control-sm mb-1" name="xml_research1"/>
                    <input type="text" class="form-control form-control-sm mb-1" name="xml_research2"/>
                    <input type="text" class="form-control form-control-sm mb-1" name="xml_research3"/>
                    <input type="text" class="form-control form-control-sm mb-1" name="xml_research4"/>
                    <input type="text" class="form-control form-control-sm mb-1" name="xml_research5"/>
                    <input type="button" class="btn btn-primary btn-sm mt-1"
                           value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarResearch.oscarDxResearch.btnCodeSearch"/>"
                           onclick="ResearchScriptAttach();">
                </div>

                <%-- Center: add/remove buttons --%>
                <div class="d-flex flex-column justify-content-center gap-2">
                    <input type="button" class="btn btn-primary btn-sm"
                           value="<fmt:setBundle basename="oscarResources"/><fmt:message key="ADD"/> >>"
                           onclick="submitform('add');">
                    <input type="button" class="btn btn-secondary btn-sm"
                           value="<< <fmt:setBundle basename="oscarResources"/><fmt:message key="REMOVE"/>"
                           onclick="submitform('remove');">
                </div>

                <%-- Right panel: current quick list items --%>
                <div style="flex:1; min-width:200px;">
                    <label class="form-label"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarResearch.oscarDxResearch.quickListItemsOf"/> <c:out value="${quickListName}"/></label>
                    <select class="form-select" name="quickListItems" size="10" multiple="true">
                        <c:forEach var="qlItems" items="${allQuickListItems.dxQuickListItemsVector}">
                            <option value="${e:forHtmlAttribute(qlItems.type)},${e:forHtmlAttribute(qlItems.dxSearchCode)}">${e:forHtml(qlItems.description)}</option>
                        </c:forEach>
                    </select>
                </div>
            </div>

            <div style="margin-top:15px;">
                <input type="button" class="btn btn-secondary"
                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnClose"/>"
                       onclick="window.close()">
            </div>
        </form>

    </div>
    </body>
</html>
