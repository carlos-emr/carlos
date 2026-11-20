
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
<%--
    Classic case-management note entry page with issue selection and soft-wrapped clinical text.
    Uses the caseManagementEntryForm model and showResolved request parameter; preserves
    patient/provider scope and typed line breaks when editing and saving.
    @since 2026-09-27 (soft-wrap and save-order documentation)
--%>



<%-- Updated by Eugene Petruhin on 24 dec 2008 while fixing #2459538 --%>
<%-- Updated by Eugene Petruhin on 09 jan 2009 while fixing #2482832 & #2494061 --%>

<%@ page import="io.github.carlos_emr.carlos.casemgmt.web.formbeans.CaseManagementEntryFormBean"%>

<%@ page import="io.github.carlos_emr.carlos.util.StringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SafeEncode" %>
<%@ include file="/WEB-INF/jsp/casemgmt/taglibs.jsp" %>
<fmt:setBundle basename="oscarResources"/>
<%@ taglib uri="/WEB-INF/caisi-tag.tld" prefix="caisi" %>


<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:message var="draftSavingLabel" key="encounter.draftSaving.msg"/>
<fmt:message var="draftSaveFailedLabel" key="encounter.draftSaveFailed.msg"/>
<fmt:message var="draftSaveConflictLabel" key="encounter.draftSaveConflict.msg"/>
<fmt:message var="draftSavedLabel" key="encounter.draftSaved.msg"/>
<fmt:message var="noteLockLostLabel" key="encounter.noteLockLostError.msg"/>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");

    boolean showResolved = false;
    try {
        showResolved = Boolean.parseBoolean(request.getParameter("showResolved"));
    } catch (NullPointerException e) {
        // do nothing it's okay to not have this parameter
    }
%>
<html>
<head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
    <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
    <title>Case Management</title>
    <c:set var="ctx" value="${pageContext.request.contextPath}" scope="request"/>
    <link rel="stylesheet" href="${carlos:forHtmlAttribute(ctx)}/css/casemgmt.css" type="text/css">
    <script type="text/javascript">
        var flag =<%= request.getAttribute("change_flag") == null ? "null" : SafeEncode.forJavaScript((String)request.getAttribute("change_flag")) %>;

        <%

            String demographicNo = request.getParameter("demographicNo");
            String sessionFrmName = io.github.carlos_emr.carlos.casemgmt.common.ChartProgramContext.formKey(request, demographicNo);
            CaseManagementEntryFormBean form=(CaseManagementEntryFormBean) session.getAttribute(sessionFrmName);
            request.setAttribute("caseManagementEntryForm", form);

        int size = (form.getIssueCheckList() != null) ? form.getIssueCheckList().size() : 0;

        if (request.getAttribute("newNote")!=null && "true".equalsIgnoreCase((String)request.getAttribute("newNote")))
        {%>
        var newNote = true;
        <%}else{%>
        var newNote = false;
        <%}

        if (request.getAttribute("issueStatusChanged")!=null && "true".equalsIgnoreCase((String)request.getAttribute("issueStatusChanged")))
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

        function validateIssuecheck() {
            return Array.prototype.some.call(document.caseManagementEntryForm.elements, function (field) {
                return field.type === 'checkbox' &&
                    /^(?:selected(?:Case|Catalog)IssueId|issueCheckList\[\d+\]\.checked)$/.test(field.name)
                    && field.checked;
            });
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

        function validateSave() {

            var str1 = "You cannot save a note when there is no issue checked, please add an issue or check a currently available issue before save.";
            var str2 = "Are you sure that you want to sign and save without changing the status of any of the issues?";
            var str3 = "Please choose encounter type before saving the note."
            var str4 = "Are you sure that you want to save without signing?";
            if (!validateEnounter()) {
                alert(str3);
                return false;
            }
            if (!validateIssuecheck()) {
                alert(str1);
                return false;
            }
            if (!validateSignStatus()) {
                if (!confirm(str4)) return false;
            }


            return true;
        }

        function toggleGroupNote(el) {
            var checked = el.checked;
            if (checked == true) {
                alert('show group dialog');
            }
        }

        var XMLHttpRequestObject = false;
        var legacyAutosaveTimer = null;
        var legacyAutosavePending = false;
        var legacyAutosaveStopped = false;
        var legacyAutosaveSuspended = false;
        var legacyNoteLockOwned = false;

        function showLegacyAutosaveStatus(state, text) {
            var status = document.getElementById('legacyAutosaveStatus');
            if (!status) return;
            status.setAttribute('data-autosave-state', state);
            status.textContent = text;
        }

        function legacyAutosaveAcknowledged(response, requestUrl) {
            if (!(response.status >= 200 && response.status < 300)
                    || typeof response.responseText !== 'string' || response.responseText.trim() !== '') return false;
            if (!response.responseURL) return true;
            try {
                var expected = new URL(requestUrl, window.location.href);
                var actual = new URL(response.responseURL, window.location.href);
                return actual.origin === expected.origin && actual.pathname === expected.pathname && actual.search === expected.search;
            } catch (invalidUrl) {
                return false;
            }
        }

        function autoSave() {
            clearTimeout(legacyAutosaveTimer);
            if (!legacyNoteLockOwned || legacyAutosavePending || legacyAutosaveStopped) return;
            XMLHttpRequestObject = window.XMLHttpRequest ? new XMLHttpRequest()
                : window.ActiveXObject ? new ActiveXObject("Microsoft.XMLHTTP") : false;
            if (XMLHttpRequestObject) {
                var obj = document.getElementById('caseNote_note');
                var xhr = XMLHttpRequestObject;
                var autosaveUrl = '<%=request.getContextPath() %>/CaseManagementEntry';
                var completed = false;
                legacyAutosavePending = true;
                showLegacyAutosaveStatus('pending', '${carlos:forJavaScriptBlock(draftSavingLabel)}');
                function finish() {
                    if (completed) return;
                    completed = true;
                    legacyAutosavePending = false;
                    if (legacyAutosaveStopped) return;
                    if (legacyAutosaveAcknowledged(xhr, autosaveUrl)) {
                        showLegacyAutosaveStatus('saved', '${carlos:forJavaScriptBlock(draftSavedLabel)}');
                    } else if (xhr.status === 409) {
                        legacyAutosaveStopped = true;
                        showLegacyAutosaveStatus('conflict', '${carlos:forJavaScriptBlock(draftSaveConflictLabel)}');
                    } else {
                        showLegacyAutosaveStatus('failed', '${carlos:forJavaScriptBlock(draftSaveFailedLabel)}');
                    }
                    setTimer();
                }
                xhr.onreadystatechange = function () { if (xhr.readyState === 4) finish(); };
                xhr.onerror = finish;
                xhr.ontimeout = finish;
                xhr.onabort = finish;
                XMLHttpRequestObject.open("POST", autosaveUrl, true);
                XMLHttpRequestObject.timeout = 45000;
                XMLHttpRequestObject.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded');

                var demographicNo = '${carlos:forJavaScript(param.demographicNo)}';
                var noteId = '<carlos:encode value='<%= String.valueOf(form.getCaseNote().getId() == null || form.getCaseNote().getId() <= 0 ? 0 : form.getCaseNote().getId()) %>' context="javaScriptBlock"/>';<%-- nosemgrep: java.jsp.jsp-scriptlet-xss.jsp-scriptlet-xss --%>
                var programId = '<%= SafeEncode.forJavaScript(io.github.carlos_emr.carlos.casemgmt.common.ChartProgramContext.resolve(request)) %>';
                var chartContext = '<%= SafeEncode.forJavaScript(io.github.carlos_emr.carlos.casemgmt.common.ChartProgramContext.token(request)) %>';
                XMLHttpRequestObject.send((chartContext ? "chartContext=" + encodeURIComponent(chartContext) + "&" : "") + "method=autosave&demographicNo=" + demographicNo + "&programId=" + programId + "&note_id=" + noteId + "&note=" + encodeURIComponent(obj.value));
            } else {
                showLegacyAutosaveStatus('failed', '${carlos:forJavaScriptBlock(draftSaveFailedLabel)}');
                setTimer();
            }
        }

        function setTimer() {
            clearTimeout(legacyAutosaveTimer);
            if (legacyNoteLockOwned && !legacyAutosaveStopped) legacyAutosaveTimer = setTimeout(autoSave, 60000);
        }

        window.addEventListener('pagehide', function () {
            if (!legacyAutosaveStopped) legacyAutosaveSuspended = true;
            legacyAutosaveStopped = true;
            clearTimeout(legacyAutosaveTimer);
            if (legacyAutosavePending && XMLHttpRequestObject) XMLHttpRequestObject.abort();
        });
        window.addEventListener('pageshow', function (event) {
            if (!event.persisted || !legacyAutosaveSuspended) return;
            legacyAutosaveSuspended = false;
            legacyAutosaveStopped = false;
            setTimer();
        });

        function init() {
            if (window.opener && !window.opener.closed) window.opener.location.reload(true);
        }

        // The reader can view and copy the note before asking to edit it.
        function takeNoteLock() {
            var edit = document.getElementById('editLegacyNote');
            if (edit.disabled || legacyNoteLockOwned) return;
            var lockRequest = window.XMLHttpRequest ? new XMLHttpRequest() : null;
            if (!lockRequest) {
                alert('${carlos:forJavaScriptBlock(noteLockLostLabel)}');
                return;
            }
            edit.disabled = true;
            lockRequest.onreadystatechange = function () {
                if (lockRequest.readyState !== 4) return;
                var owned = false;
                if (lockRequest.status === 200) {
                    try {
                        owned = JSON.parse(lockRequest.responseText).ownedBySession === true;
                    } catch (e) {
                        owned = false;
                    }
                }
                if (!owned) {
                    edit.disabled = false;
                    alert('${carlos:forJavaScriptBlock(noteLockLostLabel)}');
                    return;
                }
                legacyNoteLockOwned = true;
                edit.hidden = true;
                document.querySelectorAll('#caseManagementEntryForm [data-note-lock-edit]').forEach(function (field) {
                    field.disabled = false;
                });
                var note = document.getElementById('caseNote_note');
                note.readOnly = false;
                note.focus();
                setTimer();
            };
            lockRequest.open('POST', '<%=request.getContextPath() %>/CaseManagementEntry', true);
            lockRequest.timeout = 45000;
            lockRequest.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded');
            var demographicNo = '${carlos:forJavaScript(param.demographicNo)}';
            var noteId = '<carlos:encode value='<%= String.valueOf(form.getCaseNote().getId() == null || form.getCaseNote().getId() <= 0 ? 0 : form.getCaseNote().getId()) %>' context="javaScriptBlock"/>';<%-- nosemgrep: java.jsp.jsp-scriptlet-xss.jsp-scriptlet-xss --%>
            var chartContext = '<%= SafeEncode.forJavaScript(io.github.carlos_emr.carlos.casemgmt.common.ChartProgramContext.token(request)) %>';
            lockRequest.send((chartContext ? "chartContext=" + encodeURIComponent(chartContext) + "&" : "")
                + "method=isNoteEdited&demographicNo=" + encodeURIComponent(demographicNo)
                + "&noteId=" + encodeURIComponent(noteId));
        }

        function cancelLegacyNote(form) {
            if (legacyNoteLockOwned) {
                form.elements['method'].value = 'cancel';
                return true;
            }
            window.close();
            return false;
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
        String pId = io.github.carlos_emr.carlos.casemgmt.common.ChartProgramContext.resolve(request);
        if (pId == null) pId = "";
    %>
    <form name="caseManagementEntryForm" id="caseManagementEntryForm" action="<%=request.getContextPath() %>/CaseManagementEntry" method="post">
        <% if (io.github.carlos_emr.carlos.casemgmt.common.ChartProgramContext.token(request) != null) { %>
        <input type="hidden" name="chartContext" value="<%= io.github.carlos_emr.carlos.utility.SafeEncode.forHtmlAttribute(io.github.carlos_emr.carlos.casemgmt.common.ChartProgramContext.token(request)) %>"/>
        <% } %>
        <input type="hidden" name="chain" id="chain"/>
        <input type="hidden" name="demographicNo" id="demographicNo" value="${carlos:forHtmlAttribute(param.demographicNo)}"/>
        <c:if test="${param.providerNo==null}">
            <input type="hidden" name="providerNo" value="<carlos:encode value='<%= StringUtils.noNull((String)session.getAttribute("user")) %>' context="htmlAttribute"/>">
        </c:if>
        <c:if test="${param.providerNo!=null}">
            <input type="hidden" name="providerNo" id="providerNo" value="${carlos:forHtmlAttribute(param.providerNo)}"/>
        </c:if>
        <input type="hidden" name="caseNote.program_no" value="<carlos:encode value='<%= pId %>' context="htmlAttribute"/>"/>
        <input type="hidden" name="method" value="save"/>
        <input type="hidden" name="noteId" value="<carlos:encode value='<%= String.valueOf(form.getCaseNote().getId() == null || form.getCaseNote().getId() <= 0 ? 0 : form.getCaseNote().getId()) %>' context="htmlAttribute"/>"/>
        <c:if test="${param.from=='casemgmt'||requestScope.from=='casemgmt'}">
            <input type="hidden" name="from" value="casemgmt"/>
        </c:if>
        <input type="hidden" name="addIssue" value="null"/>
        <%-- Patient issue (casemgmt_issue id) a Delete or Change Issue button targets; never a row position (#6090). --%>
        <input type="hidden" name="caseIssueId" value=""/>
        <input type="hidden" name="issueSelectionById" value="true"/>

        <b><fmt:message key="casemanagementEntry.clientname"/>
            <I>
                <c:if test="${not empty requestScope.demoName}">
                    ${carlos:forHtml(requestScope.demoName)}
                </c:if>
                <c:if test="${empty requestScope.demoName}">
                    ${carlos:forHtml(param.demoName)}
                </c:if>
            </I>
            <br>
            &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; Age:
            <I>
                <c:if test="${not empty requestScope.demoName}">
                    ${carlos:forHtml(requestScope.demoAge)}
                </c:if>
                <c:if test="${empty requestScope.demoName}">
                    ${carlos:forHtml(param.demoAge)}
                </c:if>
            </I>
            <br>
            &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; DOB:
            <I>
                <c:if test="${not empty requestScope.demoName}">
                    ${carlos:forHtml(requestScope.demoDOB)}
                </c:if>
                <c:if test="${empty requestScope.demoName}">
                    ${carlos:forHtml(param.demoDOB)}
                </c:if>
            </I></b>
        <br><br>

        <b><fmt:message key="casemanagementEntry.issueassociationview"/></b>

        <table width="90%" border="0" cellpadding="0" cellspacing="1"
               bgcolor="#C0C0C0">
            <tr class="title">
                <td></td>
                <td><fmt:message key="casemanagementEntry.Issue"/></td>
                <td id="note-issue-acute-heading"><fmt:message key="casemanagementEntry.Acute"/></td>
                <td id="note-issue-certain-heading"><fmt:message key="casemanagementEntry.Certain"/></td>
                <td id="note-issue-major-heading"><fmt:message key="casemanagementEntry.Major"/></td>
                <td id="note-issue-resolved-heading"><fmt:message key="casemanagementEntry.Resolved"/></td>
                <td><fmt:message key="casemanagementEntry.Type"/></td>
                <td></td>
            </tr>

            <c:forEach var="issueCheckList" items="${caseManagementEntryForm.issueCheckList}" varStatus="status">
    <c:set var="cbb" value="${issueCheckList}" />
    <c:set var="writeAccess" value="${cbb.issueDisplay.writeAccess}" />
    <c:set var="disabled" value="${cbb.issueDisplay.location ne 'local' ? true : !writeAccess}" />
    <c:set var="checkBoxDisabled" value="${cbb.issueDisplay.location ne 'local' ? false : disabled}" />
    <c:set var="resolved" value="${cbb.issueDisplay.resolved eq 'resolved'}" />

    <c:if test="${!resolved or showResolved}">
        <c:set var="counter" value="${status.count}" />

        <tr style="background-color: ${counter % 2 == 0 ? '#EEEEFF' : 'white'}; text-align: center;">
            <td>
                <input type="checkbox" name="${cbb.issue.id > 0 ? 'selectedCaseIssueId' : 'selectedCatalogIssueId'}"
                       id="note-issue-select-${status.index}"
                       value="<carlos:encode value='${cbb.issue.id > 0 ? cbb.issue.id : cbb.issue.issue_id}' context='htmlAttribute'/>"
                       onchange="setChangeFlag(true);"
                       disabled ${checkBoxDisabled ? '' : 'data-note-lock-edit'} />
            </td>
            <td id="note-issue-description-${status.index}"
                style="${cbb.issueDisplay.priority eq 'allergy' ? 'background-color: yellow;' : ''}">
                <label for="note-issue-select-${status.index}">${cbb.issueDisplay.description}</label>
            </td>
            <td>
                <select id="note-issue-acute-${status.index}" name="issueCheckList[${status.index}].issueDisplay.acute"
                        aria-labelledby="note-issue-acute-heading note-issue-description-${status.index}"
                        disabled ${disabled ? '' : 'data-note-lock-edit'}>
                    <option value="acute" ${cbb.issueDisplay.acute eq 'acute' ? 'selected' : ''}>acute</option>
                    <option value="chronic" ${cbb.issueDisplay.acute eq 'chronic' ? 'selected' : ''}>chronic</option>
                </select>
            </td>
            <td>
                <select id="note-issue-certain-${status.index}" name="issueCheckList[${status.index}].issueDisplay.certain"
                        aria-labelledby="note-issue-certain-heading note-issue-description-${status.index}"
                        disabled ${disabled ? '' : 'data-note-lock-edit'}>
                    <option value="certain" ${cbb.issueDisplay.certain eq 'certain' ? 'selected' : ''}>certain</option>
                    <option value="uncertain" ${cbb.issueDisplay.certain eq 'uncertain' ? 'selected' : ''}>uncertain</option>
                </select>
            </td>
            <td>
                <select id="note-issue-major-${status.index}" name="issueCheckList[${status.index}].issueDisplay.major"
                        aria-labelledby="note-issue-major-heading note-issue-description-${status.index}"
                        disabled ${disabled ? '' : 'data-note-lock-edit'}>
                    <option value="major" ${cbb.issueDisplay.major eq 'major' ? 'selected' : ''}>major</option>
                    <option value="not major" ${cbb.issueDisplay.major eq 'not major' ? 'selected' : ''}>not major</option>
                </select>
            </td>
            <td>
                <select id="note-issue-resolved-${status.index}" name="issueCheckList[${status.index}].issueDisplay.resolved"
                        aria-labelledby="note-issue-resolved-heading note-issue-description-${status.index}"
                        disabled ${disabled ? '' : 'data-note-lock-edit'}>
                    <option value="resolved" ${cbb.issueDisplay.resolved eq 'resolved' ? 'selected' : ''}>resolved</option>
                    <option value="unresolved" ${cbb.issueDisplay.resolved eq 'unresolved' ? 'selected' : ''}>unresolved</option>
                </select>
            </td>
            <td>
                <input type="text" name="issueCheckList[${status.index}].issueDisplay.role" 
                       value="${cbb.issueDisplay.role}" disabled ${disabled ? '' : 'data-note-lock-edit'} />
            </td>
            <td>
                <c:if test="${cbb.issueDisplay.location eq 'local'}">
                    <c:if test="${!cbb.used}">
                        <button type="submit" disabled data-note-lock-edit
                                onclick="this.form.method.value='issueDelete'; this.form.caseIssueId.value=this.getAttribute('data-case-issue-id');"
                                data-case-issue-id="<carlos:encode value='${cbb.issue.id}' context='htmlAttribute'/>">
                            Delete
                        </button>
                    </c:if>
                    <button type="submit" disabled data-note-lock-edit
                            onclick="this.form.method.value='changeDiagnosis'; this.form.caseIssueId.value=this.getAttribute('data-case-issue-id');"
                            data-case-issue-id="<carlos:encode value='${cbb.issue.id}' context='htmlAttribute'/>">
                        Change Issue
                    </button>
                </c:if>
                <c:if test="${cbb.issueDisplay.location ne 'local'}">
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
               onclick="document.location='CaseManagementEntry?method=edit&note_edit=new&from=casemgmt&demographicNo=<carlos:encode value='<%= request.getParameter("demographicNo") != null ? request.getParameter("demographicNo") : "" %>' context="uriComponent"/>&providerNo=<carlos:encode value='<%= request.getParameter("providerNo") != null ? request.getParameter("providerNo") : "" %>' context="uriComponent"/>&showResolved=true<%= io.github.carlos_emr.carlos.casemgmt.common.ChartProgramContext.token(request) == null ? "" : "&chartContext=" + SafeEncode.forUriComponent(io.github.carlos_emr.carlos.casemgmt.common.ChartProgramContext.token(request)) %>'"/><%-- nosemgrep: java.jsp.jsp-scriptlet-xss.jsp-scriptlet-xss --%>
        <%
            }
        %>
        <br>
        <security:oscarSec roleName="<%=roleName$%>" objectName="_casemgmt.issues" rights="w">
            <input type="submit" value="add new issue" disabled data-note-lock-edit onclick="this.form.method.value='addNewIssue';" />
        </security:oscarSec>

        <p><b><fmt:message key="casemanagementEntry.progressnoteentryview"/> </b></p>
        <button type="button" id="editLegacyNote" onclick="takeNoteLock()"><fmt:message key="encounter.edit.msgEdit"/></button>
        <%if ("true".equalsIgnoreCase((String) request.getAttribute("change_flag"))) {%>
        <span id="spanMsg" style="color:red"><fmt:message key="casemanagementEntry.notenotsavedyet"/></span>
        <%} else {%>
        <span id="spanMsg" style="color:blue">
            <c:if test="${not empty casemgmt}">
                <c:forEach var="message" items="${casemgmt}">
                    <i>${carlos:forHtml(message)}</i>
                </c:forEach>
            </c:if>
	    </span>
        <%} %>
        <div id="legacyAutosaveStatus" role="status" aria-live="polite" aria-atomic="true"></div>

        <p>
        <table>
            <tr>
                <td class="fieldValue" colspan="1">
                    <%-- wrap="soft": a hard-wrapped textarea submits a CRLF at every visual wrap
                         point, which the server stores as part of the note (#3955). The closing
                         tag follows the value directly: any whitespace between them is textarea
                         content and would be appended to the note on every save. --%>
                    <textarea name="caseNote_note" id="caseNote_note" cols="60" rows="20" wrap="soft" readonly aria-label="<fmt:message key='encounter.noteBrowser.encounterNote'/>"
                              onchange="setChangeFlag(true);">${carlos:forHtmlContent(caseManagementEntryForm.caseNote.note)}</textarea>
                </td>
                <td class="fieldTitle"></td>

            </tr>

            <tr>
                <td class="fieldTitle"><fmt:message key="casemanagementEntry.encountertype"/></td>
                <td class="fieldValue"><select
                        name="caseNote.encounter_type" disabled data-note-lock-edit onchange="setChangeFlag(true);">
                    <option value="">&nbsp;</option>
                    <option value="face to face encounter with client"><fmt:message key="casemanagementEntry.facetofaceencounterwithclient"/></option>
                    <option value="telephone encounter with client"><fmt:message key="casemanagementEntry.telephoneencounterwithclient"/></option>
                    <option value="encounter without client"><fmt:message key="casemanagementEntry.encounterwithoutclient"/></option>
                </select></td>
            </tr>

            <tr>
                <td class="fieldTitle"><label for="note-sign"><fmt:message key="casemanagementEntry.Sign"/></label></td>
                <td class="fieldValue"><input type="checkbox" id="note-sign" name="sign" disabled data-note-lock-edit onchange="setChangeFlag(true);"/></td>
            </tr>

            <tr>
                <td class="fieldTitle"><label for="note-include-issues"><fmt:message key="casemanagementEntry.includecheckedissuesinnote"/></label></td>
                <td class="fieldValue"><input type="checkbox" id="note-include-issues" name="includeIssue" disabled data-note-lock-edit onchange="setChangeFlag(true);"/></td>
            </tr>

            <caisi:isModuleLoad moduleName="TORONTO_RFQ" reverse="true">
                <c:if test="${param.from=='casemgmt' || requestScope.from=='casemgmt'}">
                    <c:url value="${requestScope.billing_url}" var="url"/>
                    <caisirole:SecurityAccess accessName="billing" accessType="access"
                                              providerNo="${carlos:forHtmlAttribute(param.providerNo)}"
                                              demoNo="${carlos:forHtmlAttribute(param.demographicNo)}" programId="<%=pId%>">
                        <tr>
                            <td class="fieldTitle"><fmt:message key="casemanagementEntry.billing"/></td>

                            <td class="fieldValue">
                                ${carlos:forHtml(caseManagementEntryForm.caseNote.billing_code)}
                                <input type="button" value="add billing" disabled data-note-lock-edit
                                       onclick="self.open('<carlos:encode value='<%= StringUtils.noNull((String)request.getAttribute("billing_url")) %>' context="javaScriptAttribute"/>','','scrollbars=yes,menubars=no,toolbars=no,resizable=yes');return false;">
                            </td>
                        </tr>
                    </caisirole:SecurityAccess>
                </c:if>
            </caisi:isModuleLoad>

            <tr>
                <td class="fieldValue" colspan="2">
                    <input type="submit" value="Save" disabled data-note-lock-edit
                           onclick="this.form.method.value='save';return validateSave();">
                    <input type="submit"
                           value="Save and Exit" disabled data-note-lock-edit
                           onclick="this.form.method.value='saveAndExit';if (validateSave()) {return true;}else return false;">
                    <input type="submit"
                           value="cancel" onclick="return cancelLegacyNote(this.form);">
                </td>

            </tr>

        </table>
    </form>

</security:oscarSec>
<security:oscarSec roleName="<%=roleName$%>" objectName="_casemgmt.notes" rights="u" reverse="true">
    <b><fmt:message key="casemanagementEntry.encounterwithoutclient"/>You do not have permission to edit this note.</b>
</security:oscarSec>
</body>
</html>
