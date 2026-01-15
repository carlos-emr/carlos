
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

--%>



<%-- Updated by Eugene Petruhin on 24 dec 2008 while fixing #2459538 --%>
<%-- Updated by Eugene Petruhin on 09 jan 2009 while fixing #2482832 & #2494061 --%>

<%@ page import="ca.openosp.openo.casemgmt.web.formbeans.CaseManagementEntryFormBean"%>
<%@ include file="/casemgmt/taglibs.jsp" %>
<%@ taglib uri="/WEB-INF/caisi-tag.tld" prefix="caisi" %>
<%@ taglib uri="/WEB-INF/oscarProperties-tag.tld" prefix="oscarProp" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");

    boolean showResolved = false;
    try {
        showResolved = Boolean.parseBoolean(request.getParameter("showResolved"));
    } catch (NullPointerException e) {
        // do nothing it's okay to not have this parameter
    }
    int count_issues_display = 0;
%>
<html>
<head>
    <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
    <title>Case Management</title>
    <c:set var="ctx" value="${pageContext.request.contextPath}" scope="request"/>
    <link rel="stylesheet" href="<c:out value="${ctx}"/>/css/casemgmt.css" type="text/css">
    <script type="text/javascript">
        var flag =<%=request.getAttribute("change_flag")%>;

        <%

            String demographicNo = request.getParameter("demographicNo");
            String sessionFrmName = "caseManagementEntryForm" + demographicNo;
            CaseManagementEntryFormBean form=(CaseManagementEntryFormBean) session.getAttribute(sessionFrmName);
            request.setAttribute("caseManagementEntryForm", form);

        int size = (form.getIssueCheckList() != null) ? form.getIssueCheckList().size() : 0;

        if (session.getAttribute("newNote")!=null && "true".equalsIgnoreCase((String)session.getAttribute("newNote")))
        {%>
        var newNote = true;
        <%}else{%>
        var newNote = false;
        <%}

        if (session.getAttribute("issueStatusChanged")!=null && "true".equalsIgnoreCase((String)session.getAttribute("issueStatusChanged")))
        {%>
        var issueChanged = true;
        <%}else{%>
        var issueChanged = false;
        <%}%>

        var issueSize =<%=size%>;

        function setChangeFlag(change) {
            flag = change;
            document.getElementById("spanMsg").innerHTML = "This note has not been saved yet!";
            document.getElementById("spanMsg").style.color = "red";
        }

        function validateChange() {
            var str = "You haven't saved the change yet. Please save first.";
            if (flag == true) {
                alert(str);
                return false;
            }
            return true;
        }

        function validateBack() {
            var str = "You haven't saved the change yet. Please save first.";
            if (flag == true) {
                alert(str);
                return false;
            } else {
                return true;
            }
        }

        function validateIssuecheck(issueSize) {
            for (let i = 0; i < issueSize; i++) {
                //alert("checked="+document.caseManagementEntryForm.elements["issueCheckList["+i+"].checked"].checked);
                if (document.caseManagementEntryForm.elements["issueCheckList[" + i + "].checked"].checked) {
                    //alert("issue check return true");
                    return true;
                }
            }
            return false;

        }

        function validateEnounter() {
            if (document.caseManagementEntryForm.elements["caseNote.encounter_type"].value == "" || document.caseManagementEntryForm.elements["caseNote.encounter_type"].value == " ") {
                return false;
            } else {
                return true;
            }
        }

        function validateIssueStatus() {
            var signed = false;

            if (document.caseManagementEntryForm.sign.checked) signed = true;

            if (newNote == true && signed == true) {

                if (issueChanged == true) return true;
                else return false;
            }
            return true;
        }

        function validateSignStatus() {
            if (document.caseManagementEntryForm.sign.checked)
                return true;
            else
                return false;
        }

        function validateSave(count_issues_display) {

            var str1 = "You cannot save a note when there is no issue checked, please add an issue or check a currently available issue before save.";
            var str2 = "Are you sure that you want to sign and save without changing the status of any of the issues?";
            var str3 = "Please choose encounter type before saving the note."
            var str4 = "Are you sure that you want to save without signing?";
            if (!validateEnounter()) {
                alert(str3);
                return false;
            }
            if (!validateIssuecheck(count_issues_display)) {
                alert(str1);
                return false;
            }
            if (!validateSignStatus()) {
                if (!confirm(str4)) return false;
            }


            <oscarProp:oscarPropertiesCheck property="oncall" value="yes">
            var s = document.caseManagementEntryForm.elements['caseNote.encounter_type'];
            if (s.options[s.selectedIndex].value == 'telephone encounter weekdays 8am-6pm' || s.options[s.selectedIndex].value == 'telephone encounter weekends or 6pm-8am') {
                document.caseManagementEntryForm.elements['chain'].value = '/OnCallQuestionnaire.do?method=form&providerNo=' + document.caseManagementEntryForm.providerNo.value + '&type=' + s.options[s.selectedIndex].value;
            }
            </oscarProp:oscarPropertiesCheck>
            return true;
        }

        function toggleGroupNote(el) {
            var checked = el.checked;
            if (checked == true) {
                alert('show group dialog');
            }
        }

        var XMLHttpRequestObject = false;

        if (window.XMLHttpRequest) {
            XMLHttpRequestObject = new XMLHttpRequest();
        } else if (window.ActiveXObject) {
            XMLHttpRequestObject = new ActiveXObject("Microsoft.XMLHTTP");
        }

        function autoSave() {
            if (XMLHttpRequestObject) {
                var obj = document.getElementById('caseNote_note');
                XMLHttpRequestObject.open("POST", '<%=request.getContextPath() %>/CaseManagementEntry.do', true);
                XMLHttpRequestObject.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded');

                var demographicNo = '<c:out value="${param.demographicNo}"/>';
                var noteId = '<%=request.getParameter("noteId") != null ? request.getParameter("noteId") : request.getAttribute("noteId") != null ? request.getAttribute("noteId") : ""%>';
                var programId = '<c:out value="${case_program_id}"/>';
                XMLHttpRequestObject.send("method=autosave&demographicNo=" + demographicNo + "&programId=" + programId + "&note_id=" + noteId + "&note=" + escape(obj.value));
            }

            setTimer();
        }

        function setTimer() {
            setTimeout("autoSave()", 60000);
        }

        function init() {
            setTimer();
            window.opener.location.reload(true);
        }

        function restore() {
            if (confirm('You have an unsaved note from a previous session.  Click ok to retrieve note.')) {
                document.caseManagementEntryForm.method.value = 'restore';
                document.caseManagementEntryForm.submit();
            }
        }
    </script>

</head>
<body onload="init()">
<security:oscarSec roleName="<%=roleName$%>" objectName="_casemgmt.notes" rights="u">
    <%
        //get programId
        String pId = (String) session.getAttribute("case_program_id");
        if (pId == null) pId = "";
    %>
    <form action="<%=request.getContextPath() %>/CaseManagementEntry.do">
        <input type="hidden" name="chain" id="chain"/>
        <input type="hidden" name="demographicNo" id="demographicNo"/>
        <c:if test="${param.providerNo==null}">
            <input type="hidden" name="providerNo" value="<%=session.getAttribute("user")%>">
        </c:if>
        <c:if test="${param.providerNo!=null}">
            <input type="hidden" name="providerNo" id="providerNo"/>
        </c:if>
        <input type="hidden" name="caseNote.program_no" value="<%=pId%>"/>
        <input type="hidden" name="method" value="save"/>
        <c:if test="${param.from=='casemgmt'||requestScope.from=='casemgmt'}">
            <input type="hidden" name="from" value="casemgmt"/>
        </c:if>
        <input type="hidden" name="lineId" value="0"/>
        <input type="hidden" name="addIssue" value="null"/>
        <input type="hidden" name="deleteId" value="0"/>

        <b><fmt:setBundle basename="oscarResources"/><fmt:message key="casemanagementEntry.clientname"/>
            <I>
                <c:if test="${not empty requestScope.demoName}">
                    <c:out value="${requestScope.demoName}"/>
                </c:if>
                <c:if test="${empty requestScope.demoName}">
                    <c:out value="${param.demoName}"/>
                </c:if>
            </I>
            <br>
            &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; Age:
            <I>
                <c:if test="${not empty requestScope.demoName}">
                    <c:out value="${requestScope.demoAge}"/>
                </c:if>
                <c:if test="${empty requestScope.demoName}">
                    <c:out value="${param.demoAge}"/>
                </c:if>
            </I>
            <br>
            &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; DOB:
            <I>
                <c:if test="${not empty requestScope.demoName}">
                    <c:out value="${requestScope.demoDOB}"/>
                </c:if>
                <c:if test="${empty requestScope.demoName}">
                    <c:out value="${param.demoDOB}"/>
                </c:if>
            </I></b>
        <br><br>

        <b><fmt:setBundle basename="oscarResources"/><fmt:message key="casemanagementEntry.issueassociationview"/></b>

        <table width="90%" border="0" cellpadding="0" cellspacing="1"
               bgcolor="#C0C0C0">
            <tr class="title">
                <td></td>
                <td><fmt:setBundle basename="oscarResources"/><fmt:message key="casemanagementEntry.Issue"/></td>
                <td><fmt:setBundle basename="oscarResources"/><fmt:message key="casemanagementEntry.Acute"/></td>
                <td><fmt:setBundle basename="oscarResources"/><fmt:message key="casemanagementEntry.Certain"/></td>
                <td><fmt:setBundle basename="oscarResources"/><fmt:message key="casemanagementEntry.Major"/></td>
                <td><fmt:setBundle basename="oscarResources"/><fmt:message key="casemanagementEntry.Resolved"/></td>
                <td><fmt:setBundle basename="oscarResources"/><fmt:message key="casemanagementEntry.Type"/></td>
                <td></td>
            </tr>

            <c:forEach var="issueCheckList" items="${caseManagementEntryForm.issueCheckList}" varStatus="status">
    <c:set var="cbb" value="${issueCheckList}" />
    <c:set var="writeAccess" value="${cbb.issueDisplay.writeAccess}" />
    <c:set var="disabled" value="${cbb.issueDisplay.location ne 'local' ? true : !writeAccess}" />
    <c:set var="checkBoxDisabled" value="${cbb.issueDisplay.location ne 'local' ? false : disabled}" />
    <c:set var="resolved" value="${cbb.issueDisplay.resolved eq 'resolved'}" />

    <c:if test="${!resolved or showResolved}">
        <c:set var="countIssuesDisplay" value="${countIssuesDisplay + 1}" />
        <c:set var="counter" value="${status.count}" />

        <tr style="background-color: ${counter % 2 == 0 ? '#EEEEFF' : 'white'}; text-align: center;">
            <td>
                <input type="checkbox" name="issueCheckList[${status.index}].checked"
                       onchange="setChangeFlag(true);"
                       ${checkBoxDisabled ? 'disabled' : ''} />
            </td>
            <td style="${cbb.issueDisplay.priority eq 'allergy' ? 'background-color: yellow;' : ''}">
                ${cbb.issueDisplay.description}
            </td>
            <td>
                <select name="issueCheckList[${status.index}].issueDisplay.acute" ${disabled ? 'disabled' : ''}>
                    <option value="acute" ${cbb.issueDisplay.acute eq 'acute' ? 'selected' : ''}>acute</option>
                    <option value="chronic" ${cbb.issueDisplay.acute eq 'chronic' ? 'selected' : ''}>chronic</option>
                </select>
            </td>
            <td>
                <select name="issueCheckList[${status.index}].issueDisplay.certain" ${disabled ? 'disabled' : ''}>
                    <option value="certain" ${cbb.issueDisplay.certain eq 'certain' ? 'selected' : ''}>certain</option>
                    <option value="uncertain" ${cbb.issueDisplay.certain eq 'uncertain' ? 'selected' : ''}>uncertain</option>
                </select>
            </td>
            <td>
                <select name="issueCheckList[${status.index}].issueDisplay.major" ${disabled ? 'disabled' : ''}>
                    <option value="major" ${cbb.issueDisplay.major eq 'major' ? 'selected' : ''}>major</option>
                    <option value="not major" ${cbb.issueDisplay.major eq 'not major' ? 'selected' : ''}>not major</option>
                </select>
            </td>
            <td>
                <select name="issueCheckList[${status.index}].issueDisplay.resolved" ${disabled ? 'disabled' : ''}>
                    <option value="resolved" ${cbb.issueDisplay.resolved eq 'resolved' ? 'selected' : ''}>resolved</option>
                    <option value="unresolved" ${cbb.issueDisplay.resolved eq 'unresolved' ? 'selected' : ''}>unresolved</option>
                </select>
            </td>
            <td>
                <input type="text" name="issueCheckList[${status.index}].issueDisplay.role" 
                       value="${cbb.issueDisplay.role}" ${disabled ? 'disabled' : ''} />
            </td>
            <td>
                <c:if test="${cbb.issueDisplay.location eq 'local'}">
                    <c:if test="${!cbb.used}">
                        <button type="submit" 
                                onclick="this.form.method.value='issueDelete'; this.form.deleteId.value='${status.index}';">
                            Delete
                        </button>
                    </c:if>
                    <button type="submit" 
                            onclick="this.form.method.value='changeDiagnosis'; this.form.deleteId.value='${status.index}';">
                        Change Issue
                    </button>
                </c:if>
                <c:if test="${cbb.issueDisplay.location ne 'local'}">
                    <fmt:setBundle basename="oscarResources" />
                    <fmt:message key="casemanagementEntry.activecommunityissue" />
                </c:if>
            </td>
        </tr>
    </c:if>
</c:forEach>


        </table>
        <br>
        <br>
        <%
            if (showResolved) {
        %>
        <input id="hideResolved" type="button" value="Hide Resolved Issues"
               onclick="document.location=document.location.href.replace('&amp;showResolved=true','')"/>
        <%
        } else {
        %>
        <input id="showResolved" type="button" value="Show Resolved Issues"
               onclick="document.location='CaseManagementEntry.do?method=edit&note_edit=new&from=casemgmt&demographicNo=<%=request.getParameter("demographicNo")%>&providerNo=<%=request.getParameter("providerNo")%>&showResolved=true'"/>
        <%
            }
        %>
        <br>
        <security:oscarSec roleName="<%=roleName$%>" objectName="_casemgmt.issues" rights="w">
            <input type="submit" value="add new issue" onclick="this.form.method.value='addNewIssue';" />
        </security:oscarSec>

        <p><b><fmt:setBundle basename="oscarResources"/><fmt:message key="casemanagementEntry.progressnoteentryview"/> </b></p>
        <%if ("true".equalsIgnoreCase((String) request.getAttribute("change_flag"))) {%>
        <span id="spanMsg" style="color:red"><fmt:setBundle basename="oscarResources"/><fmt:message key="casemanagementEntry.notenotsavedyet"/></span>
        <%} else {%>
        <span id="spanMsg" style="color:blue">
            <c:if test="${not empty casemgmt}">
                <c:forEach var="message" items="${casemgmt}">
                    <i><c:out value="${message}" /></i>
                </c:forEach>
            </c:if>
	    </span>
        <%} %>

        <p>
        <table>
            <tr>
                <td class="fieldValue" colspan="1">
                    <textarea name="caseNote_note" id="caseNote_note" cols="60" rows="20" wrap="hard"
                              onchange="setChangeFlag(true);">${caseNote.note}
                            </textarea>
                </td>
                <td class="fieldTitle"></td>

            </tr>

            <tr>
                <td class="fieldTitle"><fmt:setBundle basename="oscarResources"/><fmt:message key="casemanagementEntry.encountertype"/></td>
                <td class="fieldValue"><select
                        name="encounter_type" onchange="setChangeFlag(true);">
                    <option value="">&nbsp;</option>>
                    <option value="face to face encounter with client"><fmt:setBundle basename="oscarResources"/><fmt:message key="casemanagementEntry.facetofaceencounterwithclient"/></option>>
                    <oscarProp:oscarPropertiesCheck property="oncall" value="yes" reverse="true">
                        <option value="telephone encounter with client"><fmt:setBundle basename="oscarResources"/><fmt:message key="casemanagementEntry.telephoneencounterwithclient"/></option>
                    </oscarProp:oscarPropertiesCheck>
                    <oscarProp:oscarPropertiesCheck property="oncall" value="yes">
                        <option value="telephone encounter weekdays 8am-6pm"><fmt:setBundle basename="oscarResources"/><fmt:message key="casemanagementEntry.telephoneencounterweekdays"/></option>
                        <option value="telephone encounter weekends or 6pm-8am"><fmt:setBundle basename="oscarResources"/><fmt:message key="casemanagementEntry.telephoneencounterweekends"/></option>
                    </oscarProp:oscarPropertiesCheck>
                    <option value="encounter without client"><fmt:setBundle basename="oscarResources"/><fmt:message key="casemanagementEntry.encounterwithoutclient"/></option>
                </select></td>
            </tr>

            <tr>
                <td class="fieldTitle"><fmt:setBundle basename="oscarResources"/><fmt:message key="casemanagementEntry.Sign"/></td>
                <td class="fieldValue"><input type="checkbox" name="sign" onchange="setChangeFlag(true);"/></td>
            </tr>

            <tr>
                <td class="fieldTitle"><fmt:setBundle basename="oscarResources"/><fmt:message key="casemanagementEntry.includecheckedissuesinnote"/></td>
                <td class="fieldValue"><input type="checkbox" name="includeIssue" onchange="setChangeFlag(true);"/></td>
            </tr>

            <caisi:isModuleLoad moduleName="TORONTO_RFQ" reverse="true">
                <c:if test="${param.from=='casemgmt' || requestScope.from=='casemgmt'}">
                    <c:url value="${sessionScope.billing_url}" var="url"/>
                    <caisirole:SecurityAccess accessName="billing" accessType="access"
                                              providerNo='<%=request.getParameter("providerNo")%>'
                                              demoNo='<%=request.getParameter("demographicNo")%>' programId="<%=pId%>">
                        <tr>
                            <td class="fieldTitle"><fmt:setBundle basename="oscarResources"/><fmt:message key="casemanagementEntry.billing"/></td>

                            <td class="fieldValue">
                                ${caseNote.billing_code}
                                <input type="button" value="add billing"
                                       onclick="self.open('<%=(String)session.getAttribute("billing_url")%>','','scrollbars=yes,menubars=no,toolbars=no,resizable=yes');return false;">
                            </td>
                        </tr>
                    </caisirole:SecurityAccess>
                </c:if>
            </caisi:isModuleLoad>

            <caisi:isModuleLoad moduleName="casemgmt.note.password.enabled">
                <tr>
                    <td class="fieldTitle"><fmt:setBundle basename="oscarResources"/><fmt:message key="casemanagementEntry.password"/></td>
                    <td class="fieldValue"><input type="password" name="password" id="password"/></td>
                </tr>
            </caisi:isModuleLoad>
            <tr>
                <td class="fieldValue" colspan="2">
                    <input type="submit" value="Save"
                           onclick="this.form.method.value='save';return validateSave(<%=count_issues_display%>);">
                    <input type="submit"
                           value="Save and Exit"
                           onclick="this.form.method.value='saveAndExit';if (validateSave(<%=count_issues_display%>)) {return true;}else return false;">
                    <input type="submit"
                           value="cancel" onclick="this.form.method.value='cancel';return true;">
                </td>

            </tr>

        </table>
    </form>

</security:oscarSec>
<security:oscarSec roleName="<%=roleName$%>" objectName="_casemgmt.notes" rights="u" reverse="true">
    <b><fmt:setBundle basename="oscarResources"/><fmt:message key="casemanagementEntry.encounterwithoutclient"/>You do not have permission to edit this note.</b>
</security:oscarSec>
</body>
</html>
