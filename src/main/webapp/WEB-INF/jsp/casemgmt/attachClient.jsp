<%--


    Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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
    Centre for Research on Inner City Health, St. Michael's Hospital,
    Toronto, Ontario, Canada


    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

--%>
<%@ include file="/taglibs.jsp" %>
<fmt:setBundle basename="oscarResources"/>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_demographic" rights="w" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_demographic");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@page import="io.github.carlos_emr.CarlosProperties" %>
<%@page import="io.github.carlos_emr.carlos.utility.SafeEncode" %>
<%@page import="io.github.carlos_emr.carlos.PMmodule.web.utils.UserRoleUtils" %>
<%@page import="java.util.*" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.Demographic" %>
<%@page import="io.github.carlos_emr.carlos.PMmodule.model.Program" %>

<%@ taglib uri="/WEB-INF/caisi-tag.tld" prefix="caisi" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%-- A whole document: CsrfGuardScriptInjectionFilter adds the CSRF client script before </head>,
     and that script is what gives the search form below its token. As a bare fragment the page got
     no script, so every search POST was refused as a CSRF violation (403). The stylesheets named
     ${request.contextPath}, which is not an EL object, and so loaded from outside the application. --%>
<!DOCTYPE html>
<html lang="${pageContext.request.locale.language}">
<head>
<title>Attach note to client</title>
<link rel="stylesheet" type="text/css"
      href='${pageContext.request.contextPath}/css/tigris.css'/>
<link rel="stylesheet" type="text/css"
      href='${pageContext.request.contextPath}/css/displaytag.css'/>

<link rel="stylesheet" type="text/css"
      href='${pageContext.request.contextPath}/share/calendar/calendar.css'/>

<link rel="stylesheet" type="text/css"
      href='${pageContext.request.contextPath}/css/topnav.css'/>
<%
    // The note this popup attaches, handed over by the action from this popup's own request. It
    // used to be read from the session, where another chart's attach popup could replace it, so
    // this popup's results attached that chart's note (issue #4673).
    String noteId$ = (String) request.getAttribute("noteId");
%>

<script>
    function resetClientFields() {
        var form = document.clientSearchForm2;
        form.elements['criteria.demographicNo'].value = '';
        form.elements['criteria.firstName'].value = '';
        form.elements['criteria.lastName'].value = '';
        form.elements['criteria.dob'].value = '';
        form.elements['criteria.chartNo'].value = '';
        // form.elements['criteria.healthCardNumber'].value='';
        // form.elements['criteria.healthCardVersion'].value='';
        // form.elements['criteria.searchOutsideDomain'].checked = true;
        // form.elements['criteria.searchUsingSoundex'].checked = true;
        // form.elements['criteria.dateFrom'].value='';
        // form.elements['criteria.dateTo'].value='';
        // form.elements['criteria.bedProgramId'].selectedIndex = 0;
        form.elements['criteria.active'].selectedIndex = 0;
        form.elements['criteria.gender'].selectedIndex = 0;
    }

    var openWindows = new Object();

    function popupPage(vheight, vwidth, name, varpage) { //open a new popup window
        if (varpage.indexOf("..") == 0) {
            varpage = ctx + varpage.substr(2);
        }
        var page = "" + varpage;
        windowprops = "height=" + vheight + ",width=" + vwidth + ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes,screenX=600,screenY=200,top=0,left=0";
        //var popup =window.open(page, "<fmt:message key="encounter.Index.popupPageWindow"/>", windowprops);
        openWindows[name] = window.open(page, name, windowprops);

        if (openWindows[name] != null) {
            if (openWindows[name].opener == null) {
                openWindows[name].opener = self;
            }
            openWindows[name].focus();
        }

    }

    function popupHelp(type) {
        alert('not yet implemented... will show term definitions');
    }

    // A client number in the results attaches this popup's note to that client and opens the
    // client's chart: a POST of the note, the client and the chart to open, with the session's
    // CSRF token (carlosPostForm refuses to send without one, and says so).
    document.addEventListener('click', function (event) {
        var link = event.target.closest ? event.target.closest('a.attach-note-to-client') : null;
        if (!link) {
            return;
        }
        event.preventDefault();
        var client = link.getAttribute('data-client');
        carlosPostForm('<%= SafeEncode.forJavaScript(request.getContextPath()) %>/encounter/IncomingEncounter', [
            ['PEAttach', 'yes'],
            ['noteId', link.getAttribute('data-note')],
            ['selectId', client],
            ['demographicNo', client],
            ['appointmentNo', '0']
        ]);
    });
</script>
<script src="${pageContext.request.contextPath}/share/javascript/carlosCsrfForm.js"></script>
</head>
<body>
<%-- Inside the body: included above the doctype, its markup put the page in quirks mode. --%>
<%@ include file="/WEB-INF/jsp/common/messages.jsp" %>
<%-- Named for resetClientFields(). The criteria are text boxes: they were rendered as checkboxes,
     which post "on" or nothing, so the popup could not find a client to attach the note to. The two
     real checkboxes post "true": Struts cannot convert a bare checkbox's "on" to a boolean. --%>
<form name="clientSearchForm2" action="${pageContext.request.contextPath}/PMmodule/ClientSearch2" method="post">
    <input type="hidden" name="method" value="attachSearch"/>
    <input type="hidden" name="noteId" value="<carlos:encode value='<%= noteId$ %>' context="htmlAttribute"/>"/>

    <div id="projecthome" class="app">
        <div class="h4">
            <h4>Search client by entering search criteria below</h4>
        </div>
        <div class="axial">
            <table border="0" cellspacing="2" cellpadding="3">
                <tr>
                    <th><label for="attachClientNo">Client No</label></th>
                    <td><input type="text" id="attachClientNo" name="criteria.demographicNo" value="${carlos:forHtmlAttribute(criteria.demographicNo)}" size="15" /></td>
                </tr>
                <tr>
                    <th><label for="attachFirstName">First Name</label></th>
                    <td><input type="text" id="attachFirstName" name="criteria.firstName" value="${carlos:forHtmlAttribute(criteria.firstName)}" size="15" /></td>
                </tr>

                <tr>
                    <th><label for="attachLastName">Last Name</label></th>
                    <td><input type="text" id="attachLastName" name="criteria.lastName" value="${carlos:forHtmlAttribute(criteria.lastName)}" size="15" /></td>
                </tr>

                <tr>
                    <th><label for="attachDob">Date of Birth</label></th>
                    <td><input type="text" id="attachDob" name="criteria.dob" value="${carlos:forHtmlAttribute(criteria.dob)}" size="15" /><br/><small>yyyy/mm/dd</small></td>
                </tr>
                <caisi:isModuleLoad moduleName="TORONTO_RFQ" reverse="true">
                    <caisi:isModuleLoad moduleName="GET_OHIP_INFO" reverse="false">
                        <tr>
                            <th><label for="attachHealthCard">Health Card Number</label></th>
                            <td><input type="text" id="attachHealthCard" name="criteria.healthCardNumber" value="${carlos:forHtmlAttribute(criteria.healthCardNumber)}" size="15" />
                                <label for="attachHealthCardVersion">Version</label>
                                <input type="text" id="attachHealthCardVersion" name="criteria.healthCardVersion" value="${carlos:forHtmlAttribute(criteria.healthCardVersion)}" size="2" /></td>
                        </tr>
                    </caisi:isModuleLoad>

                    <tr>
                        <th><label for="attachChartNo">Chart No (MRN)</label></th>
                        <td><input type="text" id="attachChartNo" name="criteria.chartNo" value="${carlos:forHtmlAttribute(criteria.chartNo)}" size="15" /></td>
                    </tr>

                    <!-- <th>Search outside of domain <a href="javascript:void(0)" onclick="popupHelp('domain')">?</a></th>
                    -->
                    <tr>
                        <caisi:isModuleLoad
                                moduleName="pmm.client.search.outside.of.domain.enabled">
                            <th><label for="attachSearchOutsideDomain">Search all clients</label> <button type="button"
                                    aria-label="Help: Search all clients" onclick="popupHelp('domain')">?</button></th>
                            <td><input type="checkbox" id="attachSearchOutsideDomain" name="criteria.searchOutsideDomain" value="true" ${criteria.searchOutsideDomain ? 'checked' : ''}/></td>
                        </caisi:isModuleLoad>
                    </tr>

                    <tr>
                        <th><label for="attachSoundex">Soundex on names</label> <button type="button"
                                aria-label="Help: Soundex on names" onclick="popupHelp('soundex')">?</button></th>
                        <td><input type="checkbox" id="attachSoundex" name="criteria.searchUsingSoundex" value="true" ${criteria.searchUsingSoundex ? 'checked' : ''}/></td>
                    </tr>
                    <tr>
                        <th><label for="attachDateFrom">Admission Date From</label></th>
                        <td><input type="text" id="attachDateFrom" name="criteria.dateFrom" value="${carlos:forHtmlAttribute(criteria.dateFrom)}" size="12" /><br/><small>yyyy/mm/dd</small>
                        </td>
                    </tr>
                    <tr>
                        <th><label for="attachDateTo">Admission Date To</label></th>
                        <td><input type="text" id="attachDateTo" name="criteria.dateTo" value="${carlos:forHtmlAttribute(criteria.dateTo)}" size="12" /><br/><small>yyyy/mm/dd</small></td>
                    </tr>
                </caisi:isModuleLoad>
                <tr>
                    <th><label for="attachStatus">Status</label></th>
                    <%-- criteria.active, which the search reads: named "active", it was never applied, so
                         discharged clients were listed whatever was chosen. A search keeps its choice. --%>
                    <td><select id="attachStatus" name="criteria.active">
                        <option value="1">Admitted</option>
                        <option value="0" ${criteria.active eq '0' ? 'selected' : ''}>Discharged</option>
                        <option value="" ${not empty criteria and empty criteria.active ? 'selected' : ''}>ALL</option>
                    </select></td>
                </tr>
                <tr>
                    <th><label for="attachGender">Gender</label></th>
                    <td><select id="attachGender" name="criteria.gender">
                        <option value="">Any</option>
                        <c:forEach var="gen" items="${genders}">
                            <option value="${gen.code}">
                                ${carlos:forHtml(gen.description)}
                            </option>
                        </c:forEach>
                    </select></td>
                </tr>
            </table>
            <table>
                <tr>
                    <td align="center"><input type="submit" value="search" /></td>
                    <td align="center"><input type="button" name="reset"
                                              value="reset" onclick="resetClientFields()"/></td>
                </tr>
            </table>
        </div>
    </div>
</form>
<br/>
<c:if test="${requestScope.clients != null}">
    <form method="post" name="mergeform" action="<%=request.getContextPath()%>/admin/MergeRecords">
        <%-- The paging and sorting links repeat the search's parameters as a GET, which CSRFGuard
             does not check; the search form's token is left out of them, and so out of URLs. --%>
        <display:table class="simple" cellspacing="2" cellpadding="3"
                       id="client" name="clients" export="false" pagesize="10"
                       requestURI="/PMmodule/ClientSearch2" excludedParams="CSRF-TOKEN">
            <display:setProperty name="paging.banner.placement" value="bottom"/>
            <display:setProperty name="basic.msg.empty_list"
                                 value="No clients found."/>
            <display:setProperty name="sort.amount" value="list"/>

                <display:column sortable="true" title="Client No" sortProperty="demographicNo" defaultorder="ascending">
                <%-- Attaching writes a copy of the note into this client's chart, so it is a POST
                     (the click handler in the head), which CSRFGuard checks; it used to be a link. --%>
                <a href="#" class="attach-note-to-client"
                   data-client="${carlos:forHtmlAttribute(client.demographicNo)}"
                   data-note="<carlos:encode value='<%= noteId$ %>' context="htmlAttribute"/>">${carlos:forHtml(client.demographicNo)}</a>
            </display:column>
            <display:column sortable="true" title="Name" sortProperty="formattedName">
                <a
                        href="javascript:popupPage(600,800,'client','${carlos:forJavaScript(pageContext.request.contextPath)}/PMmodule/ClientManager?id=${carlos:forUriComponent(client.currentRecord)}&amp;consent=${carlos:forUriComponent(consent)}')">${carlos:forHtml(client.formattedName)}</a>
            </display:column>
            <display:column sortable="true" title="Date of Birth">
                ${carlos:forHtml(client.yearOfBirth)}/${carlos:forHtml(client.monthOfBirth)}/${carlos:forHtml(client.dateOfBirth)}
            </display:column>
            <display:column sortable="true" title="Gender" sortProperty="sexDesc">
                ${carlos:forHtml(client.sexDesc)}
            </display:column>
            <display:column sortable="true" title="Chart No" sortProperty="chartNo">
                ${carlos:forHtml(client.chartNo)}
            </display:column>
            <display:column sortable="true" title="Admitted to Program">
                <c:choose>
                    <c:when test="${client.activeCount == 0}">No</c:when>
                    <c:otherwise>Yes</c:otherwise>
                </c:choose>
            </display:column>
            <display:column sortable="true" title="H&S Alert">
                <c:choose>
                    <c:when test="${client.hsAlertCount == 0}">No</c:when>
                    <c:otherwise>Yes</c:otherwise>
                </c:choose>
            </display:column>

            <security:oscarSec roleName="<%=roleName$%>" objectName="_merge"
                               rights="r">

                <display:column title="Head Record">
                    <c:choose>

                        <c:when test="${client.headRecord == null}">
                            <input type="radio" name="head"
                                   value="${carlos:forHtmlAttribute(client.demographicNo)}">
                        </c:when>
                        <c:otherwise>
                            &nbsp;
                        </c:otherwise>
                    </c:choose>
                </display:column>

                <display:column title="Include">
                    <c:choose>
                        <c:when test="${client.headRecord == null}">
                            <input type="checkbox" name="records"
                                   value="${carlos:forHtmlAttribute(client.demographicNo)}">
                        </c:when>
                        <c:otherwise>
                            &nbsp;
                        </c:otherwise>
                    </c:choose>
                </display:column>
            </security:oscarSec>


        </display:table> <security:oscarSec roleName="<%=roleName$%>" objectName="_merge"
                                            rights="r">

        <input type="hidden" name="mergeAction" value="merge"/>
        <input type="hidden" name="provider_no"
               value="<%= session.getAttribute("user") %>"/>
        <input type="hidden" name="caisiSearch" value="yes"/>
        <input type="submit" value="Merge Selected Records"/>
    </security:oscarSec> <br/>
    </form>
</c:if>
</body>
</html>
