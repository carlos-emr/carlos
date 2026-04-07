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
    dxResearchCodeSearch.jsp - Diagnosis code search results popup

    Purpose:
    Displays matching diagnosis codes from a code search and allows the user to
    select up to 5 codes. Selected codes are written back to the opener window's
    research form fields (xml_research1 through xml_research5).

    Opened as a popup from dxResearch.jsp and dxResearchEditQuickList.jsp via
    the Code Search button.

    Request Attributes:
    - allMatchedCodes: dxCodeSearchBeanHandler containing matched diagnosis codes
    Session: codeType (e.g. "icd9")

    @since 2006-01-01 (original OSCAR implementation)
--%>

<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="owasp.encoder.jakarta" prefix="e" %>


<!DOCTYPE html>
<html>
    <head>
        <%@ include file="/includes/global-head.jspf" %>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarResearch.oscarDxResearch.dxResearchCodeSearch.title"/></title>
        <script type="text/javascript">
            /**
             * Attaches a single selected code to the opener's research form fields,
             * clearing the remaining slots, then closes this popup.
             * @param {string} code - The diagnosis code to attach
             */
            function CodeAttach(code) {
                self.opener.document.forms[0].xml_research1.value = code;
                self.opener.document.forms[0].xml_research2.value = '';
                self.opener.document.forms[0].xml_research3.value = '';
                self.opener.document.forms[0].xml_research4.value = '';
                self.opener.document.forms[0].xml_research5.value = '';
                self.close();
            }

            /**
             * Attaches up to 5 checked codes from the search results to the opener's
             * research form fields (xml_research1 through xml_research5), then closes
             * this popup. Handles both single-checkbox and NodeList cases.
             */
            function CodesAttach() {
                var nbSearchCodes = document.codeSearchForm.searchCodes;
                var fields = ['xml_research1', 'xml_research2', 'xml_research3', 'xml_research4', 'xml_research5'];
                var openerForm = self.opener.document.forms[0];
                // Clear all research fields to avoid leaving stale codes from a previous selection
                for (var k = 0; k < fields.length; k++) {
                    openerForm[fields[k]].value = '';
                }
                if (nbSearchCodes.length == undefined) {
                    if (nbSearchCodes.checked) openerForm.xml_research1.value = nbSearchCodes.value;
                } else {
                    var j = 0;
                    for (var i = 0; i < nbSearchCodes.length; i++) {
                        if (nbSearchCodes[i].checked) {
                            if (j < fields.length) {
                                openerForm[fields[j]].value = nbSearchCodes[i].value;
                                j++;
                            } else {
                                break;
                            }
                        }
                    }
                }
                self.close();
            }
        </script>
    </head>

    <body>
    <div class="container">

        <%-- Page header matching search.jsp / report.jsp / tickler pattern --%>
        <div class="page-header-bar">
            <h4 class="page-header-title">
                <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" fill="currentColor" class="page-header-icon" viewBox="0 0 16 16">
                    <path d="M11.742 10.344a6.5 6.5 0 1 0-1.397 1.398h-.001q.044.06.098.115l3.85 3.85a1 1 0 0 0 1.415-1.414l-3.85-3.85a1 1 0 0 0-.115-.1zM12 6.5a5.5 5.5 0 1 1-11 0 5.5 5.5 0 0 1 11 0"/>
                </svg>
                &nbsp;<%= org.owasp.encoder.Encode.forHtml(session.getAttribute("codeType") != null ? session.getAttribute("codeType").toString().toUpperCase() : "") %>
                <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarResearch.oscarDxResearch.dxResearchCodeSearch.msgCodeSearch"/>
            </h4>
        </div>

        <form name="codeSearchForm" method="post">
            <table class="table table-sm table-striped table-hover mt-2">
                <thead>
                    <tr>
                        <th style="width:20%;"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarResearch.oscarDxResearch.dxResearchCodeSearch.msgCode"/></th>
                        <th style="width:80%;"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarResearch.oscarDxResearch.dxResearchCodeSearch.msgDescription"/></th>
                    </tr>
                </thead>
                <tbody>
                    <c:forEach var="code" items="${allMatchedCodes.dxCodeSearchBeanVector}" varStatus="loopStatus">
                        <tr>
                            <td>
                                <input type="checkbox" name="searchCodes" value="${e:forHtmlAttribute(code.dxSearchCode)}"
                                    ${code.exactMatch == 'checked' ? 'checked' : ''} />
                                ${e:forHtml(code.dxSearchCode)}
                            </td>
                            <td>${e:forHtml(code.description)}</td>
                        </tr>
                    </c:forEach>
                    <c:if test="${empty allMatchedCodes.dxCodeSearchBeanVector}">
                        <tr>
                            <td colspan="2"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarResearch.oscarDxResearch.dxResearchCodeSearch.msgNoMatch"/>.</td>
                        </tr>
                    </c:if>
                </tbody>
            </table>

            <div style="margin-top:10px;">
                <input type="button" class="btn btn-primary" name="confirm"
                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnConfirm"/>"
                       onclick="CodesAttach();">
                <input type="button" class="btn btn-secondary" name="cancel"
                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnCancel"/>"
                       onclick="window.close();">
            </div>
        </form>

    </div>
    </body>
</html>
