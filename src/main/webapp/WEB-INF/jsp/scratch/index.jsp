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
<!DOCTYPE HTML>
<%@ page import="io.github.carlos_emr.carlos.scratch.ScratchData" %>
<%@ page import="java.util.Map" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.ScratchPad" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Date" %>
<%@ page import="io.github.carlos_emr.carlos.utility.DateUtils" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar"%>
<%@ taglib uri="/WEB-INF/rewrite-tag.tld" prefix="rewrite"%>
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<c:url var="scratchUrl" value="/Scratch"/>

<%
  String user_no = (String) request.getSession().getAttribute("user");
  String userfirstname = (String) request.getSession().getAttribute("userfirstname");
  String userlastname = (String) request.getSession().getAttribute("userlastname");

  ScratchData scratchData = new ScratchData();
  Map<String, String> hashtable = scratchData.getLatest(user_no);

  String text = "";
  String id = "0";
  
  if (hashtable != null){
      text = hashtable.get("text");
      id   = hashtable.get("id");
  }
  

  List<ScratchPad> dateIdList= scratchData.getAllDates(user_no);
%>

<html lang="${pageContext.request.locale.language}">

<head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
<title><fmt:message key="ScratchPad.title"/></title>

    <%@ include file="/WEB-INF/jsp/includes/global-head.jspf" %>

    <script type="text/javascript">
        let dirty = false;
        let isSaving = false;
        let conflictBlocked = false;
        let saveUnconfirmed = false;
        let lastSavedText = "";
        const scratchSaveUrl = "${carlos:forJavaScript(scratchUrl)}";

        function setDirty() {
            dirty = saveUnconfirmed || document.getElementById('thetext').value !== lastSavedText;
            document.getElementById('dirty').value = String(dirty);
            document.getElementById('savebutton').disabled = !dirty || isSaving || conflictBlocked;
            if (dirty) document.getElementById('lastSavedTimestamp').textContent = 'Unsaved changes';
        }

        function fixHeightOfTheText() {
            const editor = document.getElementById('thetext');
            if (editor) editor.style.height = Math.max(200, window.innerHeight - editor.offsetTop - 80) + 'px';
        }
        window.addEventListener('resize', fixHeightOfTheText);
        window.addEventListener('beforeunload', function(event) {
            if (dirty || isSaving) {
                event.preventDefault();
                event.returnValue = '';
            }
        });
        window.setInterval(autoSave, 30000);

        function autoSave() {
            if (dirty && !isSaving && !conflictBlocked) checkScratch();
        }

        function checkScratch() {
            if (isSaving || conflictBlocked) return;
            const form = document.getElementById('scratch');
            const submittedText = document.getElementById('thetext').value;
            const submittedId = Number(document.getElementById('curr_id').value);
            isSaving = true;
            document.getElementById('savebutton').disabled = true;
            document.getElementById('saveError').hidden = true;
            $.ajax({
                url: scratchSaveUrl,
                type: 'POST',
                data: $(form).serialize(),
                timeout: 30000,
                dataType: 'json',
                success: function(result) {
                    isSaving = false;
                    // HTML form serialization uses CRLF, while textarea.value uses LF.
                    // Normalize only line endings; plus signs, percent escapes, entities
                    // and leading/trailing whitespace remain literal user text.
                    const savedText = result && typeof result.text === 'string'
                        ? result.text.replace(/\r\n?/g, '\n') : null;
                    if (!result || result.success !== true || !/^[1-9]\d*$/.test(String(result.id))
                            || Number(result.id) < submittedId || savedText !== submittedText) {
                        showErrorMessage('The server did not confirm this save. Your unsaved text has been kept; please retry.');
                        return;
                    }
                    document.getElementById('curr_id').value = result.id;
                    lastSavedText = savedText;
                    saveUnconfirmed = false;
                    setDirty();
                    if (!dirty) {
                        document.getElementById('lastSavedTimestamp').textContent = 'Last saved: ' + new Date().toLocaleString();
                    }
                    const versions = document.getElementById('scratchVersions');
                    if (!Array.from(versions.options).some(option => option.value === String(result.id))) {
                        versions.add(new Option(new Date().toLocaleString(), result.id), 1);
                    }
                    try {
                        if (window.opener && !window.opener.closed
                                && typeof window.opener.callRefreshTabAlerts === 'function') {
                            window.opener.callRefreshTabAlerts('oscar_scratch');
                        }
                    } catch (error) {
                        // The schedule may have closed or navigated; the save itself succeeded.
                        console.warn('Could not refresh the schedule scratchpad indicator');
                    }
                },
                error: function(xhr, status) {
                    isSaving = false;
                    conflictBlocked = xhr.status === 409;
                    if (conflictBlocked) {
                        showErrorMessage('Another window changed this scratchpad. Your unsaved text is still below. Open the current scratchpad, compare both notes, and copy over the changes you want to keep. This window will not overwrite the newer version.');
                    } else {
                        showErrorMessage(status === 'timeout'
                            ? 'Save timed out. Your unsaved text has been kept; please retry.'
                            : 'Save failed. Your unsaved text has been kept; please retry.');
                    }
                }
            });
        }

        function showErrorMessage(message) {
            // A timeout may arrive after the database committed. Even if the user
            // undid their typing meanwhile, that current text is not confirmed saved.
            saveUnconfirmed = true;
            setDirty();
            document.getElementById('saveErrorText').textContent = message;
            document.getElementById('saveError').hidden = false;
            document.getElementById('openCurrentScratch').hidden = !conflictBlocked;
            document.getElementById('retryScratch').hidden = conflictBlocked;
            document.getElementById('lastSavedTimestamp').textContent = 'Not saved';
            document.getElementById('savebutton').disabled = conflictBlocked;
        }

        function openCurrentScratch() {
            window.open(scratchSaveUrl, '_blank', 'width=900,height=700,scrollbars=yes');
        }

        function scratchpadVersionChanged() {
            // History windows must never reload an editor containing unsaved text.
            if (!dirty && !isSaving) window.location.reload();
        }

        function showVersion(id) {
            if (!/^[1-9]\d*$/.test(id)) return;
            const win = window.open(scratchSaveUrl + '?method=showVersion&id=' + encodeURIComponent(id),
                'scratchPadVersion', 'width=' + window.innerWidth + ',height=' + window.innerHeight + ',toolbar=no,scrollbars=yes');
            if (win) win.focus();
        }
    </script>
    <style>
        :root * {
            font-family: Arial, "Helvetica Neue", Helvetica, sans-serif !important;
        }

        :root * :not(h2):not(h4) {
            font-size: 12px;
            line-height: 1 !important;
            overscroll-behavior: none;
            -webkit-font-smoothing: antialiased;
            -moz-osx-font-smoothing: grayscale;
        }
        .heading {
            display: flex;
            justify-content: flex-start;
            align-items: first baseline;
            width: 100%;
        }
        .heading .page-title {
            width: 100%;
        }

        .heading .user-name {
            width: 100%;
            justify-content: center;
        }

        table {
            border-collapse: collapse;
            width: 100%;
            display:flex;
            justify-content: center;
            align-content: flex-start;
            align-items: stretch;
            flex-direction: column;
        }
        table tr {
            display:flex;
            flex-direction: row;
        }
        table tr td {
            padding: 10px;
            vertical-align: top;
        }
        table tr td.MainTableRightColumn {
            width: 100%;
            display: flex;
            flex-direction: column;
            flex:1;
        }

        .MainTableRightColumn > textarea {
            flex: 1;
        }

        textarea {
            width: 100%;
            box-sizing: border-box;
            min-height: 346px;
            background-color: #ffffff;
            box-shadow: 0 2px 4px 0 rgba(38,40,42,0.3);
            border-radius: 4px;
            border: lightgray thin solid;
            padding: 10px;
        }

    </style>
</head>

<body>
<div class="container">
    <div class="heading">
        <div class="page-title">

            <h2 style="font-size: 30px;margin-top: 20px;margin-bottom: 10px;font-weight:bold;line-height: 1.1">

                <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="currentColor" class="bi bi-card-list" viewBox="0 0 16 16">
                    <path d="M14.5 3a.5.5 0 0 1 .5.5v9a.5.5 0 0 1-.5.5h-13a.5.5 0 0 1-.5-.5v-9a.5.5 0 0 1 .5-.5zm-13-1A1.5 1.5 0 0 0 0 3.5v9A1.5 1.5 0 0 0 1.5 14h13a1.5 1.5 0 0 0 1.5-1.5v-9A1.5 1.5 0 0 0 14.5 2z"></path>
                    <path d="M5 8a.5.5 0 0 1 .5-.5h7a.5.5 0 0 1 0 1h-7A.5.5 0 0 1 5 8m0-2.5a.5.5 0 0 1 .5-.5h7a.5.5 0 0 1 0 1h-7a.5.5 0 0 1-.5-.5m0 5a.5.5 0 0 1 .5-.5h7a.5.5 0 0 1 0 1h-7a.5.5 0 0 1-.5-.5m-1-5a.5.5 0 1 1-1 0 .5.5 0 0 1 1 0M4 8a.5.5 0 1 1-1 0 .5.5 0 0 1 1 0m0 2.5a.5.5 0 1 1-1 0 .5.5 0 0 1 1 0"></path>
                </svg>

              <fmt:message key="ScratchPad.title"/>
            </h2>
        </div>
          <div class="user-name" >
              <h4><carlos:encode value='<%= userfirstname %>' context="html"/> <carlos:encode value='<%= userlastname %>' context="html"/></h4>
          </div>
    </div>

    <table class="MainTable table-sm table-borderless" id="scrollNumber1">

	<tr>
		<td class="MainTableLeftColumn" id="tablelle" >
            <button type="button" style="margin-bottom: 8px;" class="btn btn-primary" onclick="checkScratch('Save button...')" id="savebutton">Save</button>

			<select id="scratchVersions" class="form-select" onChange="showVersion(this.options[this.selectedIndex].value)">
				<option value="showVersion">Select Version to Display</option>
				<% 
				for( ScratchPad scratchPad : dateIdList ) {
				    String strId = scratchPad.getId() + "";
				    Date date = scratchPad.getDateTime();
				    
				%>
					<option value="<carlos:encode value='<%= strId %>' context="htmlAttribute"/>"><%=DateUtils.formatDateTime(date, request.getLocale())%></option>
				<%
				}
				%>
			</select>

            <div id="lastSavedTimestamp" style="color: #666; margin-top: 8px; min-height: 20px;"></div>
	    </td>

		<td class="MainTableRightColumn" id="mainRight">
		<div id="saveError" role="alert" hidden>
            <p id="saveErrorText"></p>
            <button type="button" id="retryScratch" onclick="checkScratch()">Retry save</button>
            <button type="button" id="openCurrentScratch" onclick="openCurrentScratch()" hidden>Open current scratchpad</button>
        </div>
        <form id="scratch" action="${carlos:forHtmlAttribute(scratchUrl)}" method="post">
            <input type="hidden" name="id" id="curr_id" value="<carlos:encode value='<%= id %>' context="htmlAttribute"/>" />
            <input type="hidden" name="windowId" id="windowId" value="<%=String.valueOf(System.nanoTime())%>" />
            <input type="hidden" name="dirty" value=false id="dirty" />
            <textarea name="scratchpad" id="thetext" rows="50"
			cols="50" oninput="setDirty();" onpaste="setDirty();" ><carlos:encode value='<%= text %>' context="html"/></textarea>

        </form>
		</td>
	</tr>
</table>

<script type="text/javascript">
fixHeightOfTheText(); // fix it first time in.
lastSavedText = document.getElementById('thetext').value;
setDirty();
</script>
</div>
</body>
</html>
