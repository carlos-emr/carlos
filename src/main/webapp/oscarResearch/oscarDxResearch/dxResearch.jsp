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
    dxResearch.jsp - Disease Registry main page

    Purpose:
    Primary interface for viewing and managing a patient's diagnosis codes.
    Left panel provides code entry fields, coding system selector, code search
    popup, and a quick list sidebar. Right panel displays the patient's active
    and resolved diagnoses with actions (resolve, delete, update date).

    Loaded via setupDxResearch.do from the patient encounter or demographic view.

    Request Attributes:
    - demographicNo: Patient ID
    - providerNo: Current provider
    - codingSystem: Available coding systems
    - allDiagnostics: Patient's current diagnoses
    - allQuickLists / allQuickListItems: Quick list data (via dxQuickList.jsp include)

    Security:
    - Requires "_dxresearch" read privilege; write access controls editing

    @since 2006-01-01 (original OSCAR implementation)
--%>

<%@ page import="io.github.carlos_emr.carlos.dxresearch.util.dxResearchCodingSystem" %>
<%@ page import="io.github.carlos_emr.carlos.services.security.SecurityManager" %>

<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="owasp.encoder.jakarta" prefix="e" %>

<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_dxresearch" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_dxresearch");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }

    boolean disable;
    SecurityManager sm = new SecurityManager();

    // Check to see if the currently logged in role has write access, if so, disable input fields present in the page
    if (sm.hasWriteAccess("_dx.code", roleName$)) {
        disable = false;
    } else {
        disable = true;
    }

    // Set a String based on the "disable" boolean for easy access to use html functionality of "disabled" attribute
    String disabled = disable ? "disabled" : "";

    boolean showQuicklist = false;

    if (sm.hasWriteAccess("_dx.quicklist", roleName$)) {
        showQuicklist = true;
    }

    String user_no = (String) session.getAttribute("user");
    String color = "";
    int Count = 0;

    pageContext.setAttribute("showQuicklist", showQuicklist);
    pageContext.setAttribute("disable", disable);
    pageContext.setAttribute("disabled", disabled);
%>

<!DOCTYPE html>
<html>
    <head>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarResearch.oscarDxResearch.dxResearch.title"/></title>

        <%@ include file="/includes/global-head.jspf" %>
        <script type="text/javascript"
                src="${pageContext.servletContext.contextPath}/library/jquery/jquery-ui-1.14.2.min.js"></script>
        <script type="text/javascript"
                src="${pageContext.servletContext.contextPath}/library/oscar-modal-dialog.js"></script>
        <link rel="stylesheet" type="text/css" href="${pageContext.servletContext.contextPath}/oscarResearch/oscarDxResearch/dxResearch.css">

        <script type="text/javascript">
            //<!--

            function setfocus() {
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

            function popPage(url) {
                awnd = rs('', url, 400, 200, 1);
                awnd.focus();
            }

            var awnd = null;

            function ResearchScriptAttach() {
                var t0 = encodeURIComponent(document.forms[0].xml_research1.value);
                var t1 = encodeURIComponent(document.forms[0].xml_research2.value);
                var t2 = encodeURIComponent(document.forms[0].xml_research3.value);
                var t3 = encodeURIComponent(document.forms[0].xml_research4.value);
                var t4 = encodeURIComponent(document.forms[0].xml_research5.value);
                var codeType = document.forms[0].selectedCodingSystem.value;
                var demographicNo = encodeURIComponent(document.forms[0].demographicNo.value);

                awnd = rs('att', '${pageContext.request.contextPath}/oscarResearch/oscarDxResearch/dxResearchCodeSearch.do?codeType=' + codeType + '&xml_research1=' + t0 + '&xml_research2=' + t1 + '&xml_research3=' + t2 + '&xml_research4=' + t3 + '&xml_research5=' + t4 + '&demographicNo=' + demographicNo, 600, 600, 1);
                awnd.focus();
            }

            function submitform(target, sysCode) {
                document.forms[0].forward.value = target;

                if (sysCode != '')
                    document.forms[0].selectedCodingSystem.value = sysCode;

                document.forms[0].submit()
            }

            function set(target) {
                document.forms[0].forward.value = target;
            }


            function openNewPage(vheight, vwidth, varpage) {
                var page = varpage;
                windowprops = "height=" + vheight + ",width=" + vwidth + ",location=no,scrollbars=no,menubars=no,toolbars=no,resizable=no,screenX=0,screenY=0,top=0,left=0";
                var popup = window.open(varpage, "<fmt:setBundle basename="oscarResources"/><fmt:message key="global.oscarComm"/>", windowprops);
                popup.focus();
            }

            document.onkeypress = processKey;

            function processKey(e) {
                if (e == null) {
                    e = window.event;
                } else if (e.keyCode == 13) {
                    ResearchScriptAttach();
                }
            }

            function showdatebox(x) {
                document.getElementById("startdatenew" + x).style.display = '';
                document.getElementById("startdate1st" + x).style.display = 'none';
            }

            function update_date(did, demoNo, provNo) {
                var startdate = document.getElementById("startdatenew" + did).value;
                submitDxAction('', startdate, did, demoNo, provNo);
            }

            /** Submits a diagnosis research action via the hidden #dxResearchActionForm. */
            function submitDxAction(status, startdate, did, demoNo, provNo) {
                var form = document.getElementById('dxResearchActionForm');
                form.elements['status'].value = status;
                form.elements['startdate'].value = startdate || '';
                form.elements['did'].value = did;
                form.elements['demographicNo'].value = demoNo;
                form.elements['providerNo'].value = provNo;
                form.submit();
            }

            /** Navigates back to the opener window or browser history. */
            function handleBackNavigation() {
                try {
                    if (window.opener && !window.opener.closed) {
                        window.opener.location.reload();
                        window.close();
                    } else if (window.history.length > 1) {
                        window.history.back();
                    } else {
                        window.close();
                    }
                } catch (e) {
                    window.history.back();
                }
            }

            //-->
        </script>

    </head>

    <body onLoad="setfocus();">
    <div class="wrapper">

        <%-- Page header matching search.jsp / report.jsp / tickler pattern --%>
        <div class="page-header-bar">
            <h4 class="page-header-title">
                <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" fill="currentColor" class="page-header-icon" viewBox="0 0 16 16">
                    <path d="M8 15A7 7 0 1 1 8 1a7 7 0 0 1 0 14m0 1A8 8 0 1 0 8 0a8 8 0 0 0 0 16"/>
                    <path d="M5.255 5.786a.237.237 0 0 0 .241.247h.825c.138 0 .248-.113.266-.25.09-.656.54-1.134 1.342-1.134.686 0 1.314.343 1.314 1.168 0 .635-.374.927-.965 1.371-.673.489-1.206 1.06-1.168 1.987l.003.217a.25.25 0 0 0 .25.246h.811a.25.25 0 0 0 .25-.25v-.105c0-.718.273-.927 1.01-1.486.609-.463 1.244-.977 1.244-2.056 0-1.511-1.276-2.241-2.673-2.241-1.267 0-2.655.59-2.75 2.286m1.557 5.763c0 .533.425.927 1.01.927.609 0 1.028-.394 1.028-.927 0-.552-.42-.94-1.029-.94-.584 0-1.009.388-1.009.94"/>
                </svg>
                &nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="global.disease"/>
            </h4>
            <span><oscar:nameage demographicNo="${ demographicNo }"/></span>
        </div>

        <table>
            <tr>
                <td><form action="${pageContext.request.contextPath}/oscarResearch/oscarDxResearch/dxResearch.do" method="post">
                    <table>
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
                        <tr>
                            <td id="codeSelectorTable">

                                <table>
                                    <tr>
                                        <td>
                                            <div class="input-group">
								<span class="input-group-text" id="basic-addon3">
									<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarResearch.oscarDxResearch.codingSystem"/>
								</span>

                                                <select class="form-select" name="selectedCodingSystem"
                                                            <%=disabled%>>
                                                    <c:forEach var="codingSys" items="${codingSystem.codingSystems}">
                                                        <option value="${codingSys}">
                                                            <c:out value="${codingSys}"/>
                                                        </option>
                                                    </c:forEach>
                                                </select>
                                            </div>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td><input type="text" class="form-control" name="xml_research1"
                                                    <%=disabled%> />
                                            <input type="hidden" name="demographicNo"
                                                   value="<c:out value="${demographicNo}"/>">
                                            <input type="hidden" name="providerNo"
                                                   value="<c:out value="${providerNo}"/>"></td>
                                    </tr>
                                    <tr>
                                        <td><input type="text" class="form-control" name="xml_research2"
                                                       <%=disabled%>/></td>
                                    </tr>
                                    <tr>
                                        <td><input type="text" class="form-control" name="xml_research3"
                                                       <%=disabled%>/></td>
                                    </tr>
                                    <tr>
                                        <td><input type="text" class="form-control" name="xml_research4"
                                                       <%=disabled%>/></td>
                                    </tr>
                                    <tr>
                                        <td><input type="text" class="form-control" name="xml_research5"
                                                       <%=disabled%>/></td>
                                    </tr>
                                    <tr>
                                        <td>
                                            <input type="hidden" name="forward" value="none"/>
                                            <%if (!disable) { %>
                                            <input type="button" name="codeSearch" class="btn btn-primary"
                                                   value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarResearch.oscarDxResearch.btnCodeSearch"/>"
                                                   onClick="javascript: ResearchScriptAttach();">

                                            <input type="button" name="codeAdd" class="btn btn-primary"
                                                   value="<fmt:setBundle basename="oscarResources"/><fmt:message key="ADD"/>"
                                                   onClick="javascript: submitform('','');">

                                            <% } else { %>

                                            <input type="button" name="button" class="btn btn-primary"
                                                   value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarResearch.oscarDxResearch.btnCodeSearch"/>"
                                                   onClick="javascript: ResearchScriptAttach();"
                                                   <%=disabled%>>

                                            <input type="button" name="button" class="btn btn-primary"
                                                   value="<fmt:setBundle basename="oscarResources"/><fmt:message key="ADD"/>"
                                                   onClick="javascript: submitform('','');" <%=disabled%>>
                                            <% } %>
                                        </td>
                                    </tr>

                                        <%-- DX QUICK LIST - returns a table --%>
                                    <c:if test="${showQuicklist == true}">
                                        <tr>
                                            <td>
                                                <jsp:include page="dxQuickList.jsp">
                                                    <jsp:param value="false" name="disable"/>
                                                    <jsp:param value="${ param.quickList }" name="quickList"/>
                                                    <jsp:param value="${ demographicNo }" name="demographicNo"/>
                                                    <jsp:param value="${ providerNo }" name="providerNo"/>
                                                </jsp:include>
                                            </td>
                                        </tr>
                                    </c:if>
                                        <%-- DX QUICK LIST --%>

                                </table>

                            </td>
                            <td id="displayDxCodeTable">

                                <table>
                                    <tr>
                                        <th><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarResearch.oscarDxResearch.dxResearch.msgSystem"/></th>
                                        <th class="heading"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarResearch.oscarDxResearch.dxResearch.msgCode"/></th>
                                        <th class="heading"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarResearch.oscarDxResearch.dxResearch.msgDiagnosis"/></th>
                                        <th class="heading"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarResearch.oscarDxResearch.dxResearch.msgFirstVisit"/></th>
                                        <th class="heading"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarResearch.oscarDxResearch.dxResearch.msgLastVisit"/></th>
                                        <% if (!disable) { %>
                                        <th class="heading"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarResearch.oscarDxResearch.dxResearch.msgAction"/></th>
                                        <%} %>
                                    </tr>
                                    <c:forEach var="diagnotics" items="${allDiagnostics.dxResearchBeanVector}" varStatus="ctr">
                                        <c:choose>
                                            <c:when test="${diagnotics.status == 'A'}">
                                                <tr>
                                                    <td><c:out value="${diagnotics.type}"/></td>
                                                    <td class="notResolved"><c:out value="${diagnotics.dxSearchCode}"/></td>
                                                    <td class="notResolved"><c:out value="${diagnotics.description}"/></td>
                                                    <td class="notResolved">
                                                        <a href="#" onclick="showdatebox(${diagnotics.dxResearchNo});">
                                                            <div id="startdate1st${diagnotics.dxResearchNo}">
                                                                <c:out value="${diagnotics.start_date}"/>
                                                            </div>
                                                            <input class="form-control" id="startdatenew${diagnotics.dxResearchNo}"
                                                                   type="text" name="start_date" size="8"
                                                                   value="${e:forHtmlAttribute(diagnotics.start_date)}" style="display:none"/>
                                                        </a>
                                                    </td>
                                                    <td class="notResolved"><c:out value="${diagnotics.end_date}"/></td>
                                                    <c:if test="${not disable}">
                                                        <td class="notResolved">
                                                            <a href="#" onclick="submitDxAction('C','','${diagnotics.dxResearchNo}','${demographicNo}','${providerNo}'); return false;">
                                                                <fmt:message key="oscarResearch.oscarDxResearch.dxResearch.btnResolve"/>
                                                            </a>
                                                            <a href="#" onclick="if(confirm('Are you sure you would like to delete: ${e:forJavaScript(diagnotics.description)} ?')){submitDxAction('D','','${diagnotics.dxResearchNo}','${demographicNo}','${providerNo}');} return false;">
                                                                <fmt:message key="oscarResearch.oscarDxResearch.dxResearch.btnDelete"/>
                                                            </a>
                                                            <a href="#" onclick="update_date(${diagnotics.dxResearchNo}, ${demographicNo}, ${providerNo});">
                                                                <fmt:message key="oscarResearch.oscarDxResearch.dxResearch.btnUpdate"/>
                                                            </a>
                                                        </td>
                                                    </c:if>
                                                </tr>
                                            </c:when>
                                            <c:when test="${diagnotics.status == 'C'}">
                                                <tr>
                                                    <td><c:out value="${diagnotics.dxSearchCode}"/></td>
                                                    <td><c:out value="${diagnotics.description}"/></td>
                                                    <td><c:out value="${diagnotics.start_date}"/></td>
                                                    <td><c:out value="${diagnotics.end_date}"/></td>
                                                    <c:if test="${not disable}">
                                                        <td>
                                                            <fmt:message key="oscarResearch.oscarDxResearch.dxResearch.btnResolve"/> |
                                                            <a href="#" onclick="if(confirm('Are you sure you would like to delete this?')){submitDxAction('D','','${diagnotics.dxResearchNo}','${demographicNo}','${providerNo}');} return false;">
                                                                <fmt:message key="oscarResearch.oscarDxResearch.dxResearch.btnDelete"/>
                                                            </a>
                                                        </td>
                                                    </c:if>
                                                </tr>
                                            </c:when>
                                        </c:choose>
                                    </c:forEach>

                                </table>

                                <%-- Back button below the diagnosis list — stays at the bottom
                                     of the right column and moves down as more items are added --%>
                                <div class="mt-2 text-end">
                                    <input type="button" class="btn btn-secondary"
                                           value="<fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnBack"/>"
                                           onclick="handleBackNavigation();">
                                </div>

                            </td>
                        </tr>
                    </table>
                </form>
                </td>
            </tr>
        </table>
    </div>
    <form id="dxResearchActionForm" method="post" action="dxResearchUpdate.do" style="display:none">
        <input type="hidden" name="status" value="">
        <input type="hidden" name="startdate" value="">
        <input type="hidden" name="did" value="">
        <input type="hidden" name="demographicNo" value="">
        <input type="hidden" name="providerNo" value="">
    </form>
    </body>
</html>

