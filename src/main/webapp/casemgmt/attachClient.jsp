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
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_demographic" rights="w" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_demographic");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@ include file="/common/messages.jsp" %>
<%@page import="io.github.carlos_emr.CarlosProperties" %>
<%@page import="io.github.carlos_emr.carlos.PMmodule.web.utils.UserRoleUtils" %>
<%@page import="java.util.*" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.Demographic" %>
<%@page import="io.github.carlos_emr.carlos.PMmodule.model.Program" %>

<%@ taglib uri="/WEB-INF/caisi-tag.tld" prefix="caisi" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<link rel="stylesheet" type="text/css"
      href='${request.contextPath}/css/tigris.css'/>
<link rel="stylesheet" type="text/css"
      href='${request.contextPath}/css/displaytag.css'/>

<link rel="stylesheet" type="text/css"
      href='${request.contextPath}/share/calendar/skins/aqua/theme.css'/>

<link rel="stylesheet" type="text/css"
      href='${request.contextPath}/css/topnav.css'/>

<!-- style type="text/css">
@import "${request.contextPath}/css/tigris.css";
@import "${request.contextPath}/css/displaytag.css";
@import "${request.contextPath}/jsCalendar/skins/aqua/theme.css";
</style -->
<%
    String noteId$ = (String) session.getAttribute("noteId");
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
        //var popup =window.open(page, "<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.Index.popupPageWindow"/>", windowprops);
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
</script>
<form action="${pageContext.request.contextPath}/PMmodule/ClientSearch2.do" method="post">
    <input type="hidden" name="method" value="attachSearch"/>
    <input type="hidden" name="method" value="attachSearch"/>

    <div id="projecthome" class="app">
        <div class="h4">
            <h4>Search client by entering search criteria below</h4>
        </div>
        <div class="axial">
            <table border="0" cellspacing="2" cellpadding="3">
                <tr>
                    <th>Client No</th>
                    <td><input type="checkbox" name="criteria.demographicNo" size="15" /></td>
                </tr>
                <tr>
                    <th>First Name</th>
                    <td><input type="checkbox" name="criteria.firstName" size="15" /></td>
                </tr>

                <tr>
                    <th>Last Name</th>
                    <td><input type="checkbox" name="criteria.lastName" size="15" /></td>
                </tr>

                <tr>
                    <th>Date of Birth</th>
                    <td><input type="checkbox" name="criteria.dob" size="15" /><br/><font size="1">yyyy/mm/dd</font></td>
                </tr>
                <caisi:isModuleLoad moduleName="TORONTO_RFQ" reverse="true">
                    <caisi:isModuleLoad moduleName="GET_OHIP_INFO" reverse="false">
                        <tr>
                            <th>Health Card Number</th>
                            <td><input type="checkbox" name="criteria.healthCardNumber" size="15" />
                                <input type="checkbox" name="criteria.healthCardVersion" size="2" /></td>
                        </tr>
                    </caisi:isModuleLoad>

                    <tr>
                        <th>Chart No (MRN)</th>
                        <td><input type="checkbox" name="criteria.chartNo" size="15" /></td>
                    </tr>

                    <!-- <th>Search outside of domain <a href="javascript:void(0)" onclick="popupHelp('domain')">?</a></th>
                    -->
                    <tr>
                        <caisi:isModuleLoad
                                moduleName="pmm.client.search.outside.of.domain.enabled">
                            <th>Search all clients <a href="javascript:void(0)"
                                                      onclick="popupHelp('domain')">?</a></th>
                            <td><input type="checkbox" name="criteria.searchOutsideDomain"/></td>
                        </caisi:isModuleLoad>
                    </tr>

                    <tr>
                        <th>Soundex on names <a href="javascript:void(0)"
                                                onclick="popupHelp('soundex')">?</a></th>
                        <td><input type="checkbox" name="criteria.searchUsingSoundex"/></td>
                    </tr>
                    <tr>
                        <th>Admission Date From</th>
                        <td><input type="checkbox" name="criteria.dateFrom" size="12" /><br/><font size="1">yyyy/mm/dd</font>
                        </td>
                    </tr>
                    <tr>
                        <th>Admission Date To</th>
                        <td><input type="checkbox" name="criteria.dateTo" size="12" /><br/><font size="1">yyyy/mm/dd</font></td>
                    </tr>
                </caisi:isModuleLoad>
                <tr>
                    <th>Status</th>
                    <td><select name="active">
                        <option value="1">Admitted</option>
                        <option value="0">Discharged</option>
                        <option value="">ALL</option>
                    </select></td>
                </tr>
                <tr>
                    <th>Gender</th>
                    <td><select name="criteria.gender">
                        <option value="">Any</option>
                        <c:forEach var="gen" items="${genders}">
                            <option value="${gen.code}">
                                <c:out value="${gen.description}"/>
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
    <form method="post" name="mergeform" action="<%=request.getContextPath()%>/admin/MergeRecords.do">
        <display:table class="simple" cellspacing="2" cellpadding="3"
                       id="client" name="clients" export="false" pagesize="10"
                       requestURI="/PMmodule/ClientSearch2.do">
            <display:setProperty name="paging.banner.placement" value="bottom"/>
            <display:setProperty name="basic.msg.empty_list"
                                 value="No clients found."/>
            <display:setProperty name="sort.amount" value="list"/>

            <display:column sortable="true" title="Client No" sortProperty="demographicNo" defaultorder="ascending">
                <a
                        href="<%=request.getContextPath() %><%=request.getContextPath() %>/oscarEncounter/IncomingEncounter.do?selectId=<c:out value="${client.demographicNo}"/>&demographicNo=<c:out value="${client.demographicNo}"/>&PEAttach=yes&appointmentNo=0&noteId=<%=noteId$%>"><c:out
                        value="${client.demographicNo}"/></a>
            </display:column>
            <display:column sortable="true" title="Name" sortProperty="formattedName">
                <a
                        href="javascript:popupPage(600,800,'client','<%=request.getContextPath() %>/PMmodule/ClientManager.do?id=<c:out value="${client.currentRecord}"/>&consent=<c:out value="${consent}"/>')"><c:out
                        value="${client.formattedName}"/></a>
            </display:column>
            <display:column sortable="true" title="Date of Birth">
                <c:out value="${client.yearOfBirth}"/>/<c:out
                    value="${client.monthOfBirth}"/>/<c:out
                    value="${client.dateOfBirth}"/>
            </display:column>
            <display:column sortable="true" title="Gender" sortProperty="sexDesc">
                <c:out value="${client.sexDesc}"/>
            </display:column>
            <display:column sortable="true" title="Chart No" sortProperty="chartNo">
                <c:out value="${client.chartNo}"/>
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
                                   value="<c:out value="${client.demographicNo}" />">
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
                                   value="<c:out value="${client.demographicNo}" />">
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
