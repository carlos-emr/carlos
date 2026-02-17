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

<%@ page import="org.owasp.encoder.Encode" %>
<%@ page import="io.github.carlos_emr.carlos.scratch.ScratchData" %>
<%@ page import="java.util.Map" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.ScratchPad" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Date" %>
<%@ page import="io.github.carlos_emr.carlos.utility.DateUtils" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar"%>
<%@ taglib uri="/WEB-INF/rewrite-tag.tld" prefix="rewrite"%>

<%
  String user_no = (String) request.getSession().getAttribute("user");
  String userfirstname = (String) request.getSession().getAttribute("userfirstname");
  String userlastname = (String) request.getSession().getAttribute("userlastname");

  ScratchData scratchData = new ScratchData();
  Map<String, String> hashtable = scratchData.getLatest(user_no);

  String text = "";
  String id = "";
  
  if (hashtable != null){
      text = hashtable.get("text");
      id   = hashtable.get("id");
  }
  

  List<ScratchPad> dateIdList= scratchData.getAllDates(user_no);
%>

<html lang="en">

<head>
<title><fmt:setBundle basename="oscarResources"/><fmt:message key="ScratchPad.title"/></title>

    <%@ include file="/includes/global-head.jspf" %>

    <script type="text/javascript">
        let dirty = false;
        let isSaving = false;
        let saveTimeout = null;
        let currentText = "";
        let lastSavedText = "";
		const context = "<%=Encode.forJavaScript(request.getContextPath())%>";

        function setDirty(){
            dirty = true;
            document.getElementById('dirty').value = true;
            document.getElementById('savebutton').disabled = false;
        }

        function fixHeightOfTheText(){
            let t = document.getElementById("thetext");
            let h = window.innerHeight ? window.innerHeight : (t.parentNode ? t.parentNode.offsetHeight : 0);
            if (t && h > 0) {
                t.style.height = Math.max(200, (h - t.offsetTop - 80)) + "px";
            }
        }

        window.addEventListener('resize', fixHeightOfTheText);

        let autoSaveInterval = window.setInterval(autoSave, 30000);
    
        function autoSave(){
            if(dirty && !isSaving
                && isTextDifferent(lastSavedText, document.getElementById("thetext").value)){
				checkScratch("Auto-saving...");
            }
        }

        function checkScratch(action){
			console.debug('Action: ' + action);
            if (isSaving) {
                console.warn('Save already in progress, skipping duplicate request');
                return;
            }
            isSaving = true;
            let url = context + "/Scratch.do";
            let timeoutId = setTimeout(() => {
                // Abort ongoing AJAX request if still pending
                $.ajaxStop();
            }, 30000); // 30 second timeout

            $.ajax({
                url: url,
                type: 'POST',
                data: $('#scratch').serialize(),
                timeout: 30000,
                dataType: 'json',
                success: function(responseText) {
                    clearTimeout(timeoutId);
                    isSaving = false;
                    try {
						// console.debug(responseText);
                        // Parse URL-encoded response
                        lastSavedText = responseText['text'] || '';
                        followUp(responseText);
	                    updateSavedTimestamp();
                    } catch (e) {
                        console.error('Error parsing response:', e);
                        showErrorMessage('Invalid server response received');
                    }
                },
                error: function(jqXHR, textStatus, errorThrown) {
                    clearTimeout(timeoutId);
                    isSaving = false;
                    if (textStatus === 'timeout') {
                        console.error('Save request timed out');
                        showErrorMessage('Save operation timed out. Please try again.');
                    } else if (textStatus === 'error') {
                        console.error('Save failed:', errorThrown);
                        showErrorMessage('An error occurred and your data could not be saved!');
                    } else {
                        console.error('Save failed:', textStatus);
                        showErrorMessage('An error occurred and your data could not be saved!');
                    }
                }
            });
        }

        function showErrorMessage(message) {
	        let mainRight = document.getElementById("mainRight");
	        if (mainRight) {
		        mainRight.textContent = "";
		        let h1 = document.createElement('h1');
		        h1.style.color = 'red';
		        h1.textContent = message;
		        mainRight.appendChild(h1);

		        let h2 = document.createElement('h2');
		        h2.textContent = 'Please try again or close the window.';
		        mainRight.appendChild(h2);

		        let retryBtn = document.createElement('button');
		        retryBtn.textContent = 'Retry';
		        retryBtn.onclick = () => location.reload();
		        mainRight.appendChild(retryBtn);

		        let closeBtn = document.createElement('button');
		        closeBtn.textContent = 'Close';
		        closeBtn.onclick = () => window.close();
		        mainRight.appendChild(closeBtn);
	        }
        }

        function updateSavedTimestamp() {
	        let now = new Date();
	        let timeString = now.toLocaleString();
	        let timestampDiv = document.getElementById('lastSavedTimestamp');
	        if (timestampDiv) {
		        timestampDiv.textContent = 'Last saved: ' + timeString;
	        }
        }

        function followUp(hash){
            let latestId = hash['id'] || '';
            let latestText = hash['text'] || '';
            let windowId = hash['windowId'] || '';
            let currDirty = document.getElementById('dirty').value;
            let currId = document.getElementById('curr_id').value || 0;
            let latestIdNum = latestId;
	        // console.debug(hash);
            console.debug('Response received - dirty: ' + currDirty + ', currId: ' + currId + ', latestId: ' + latestIdNum);

            if (! currDirty) {
                // No local changes, update if server has newer version
                if (currId < latestIdNum) {
	                console.debug('Updating from server version ' + latestIdNum);
                    document.getElementById('curr_id').value = latestId;
                    document.getElementById('thetext').value = decodeQueryValue(latestText);
                }
                setClean();
            } else {
                // Local changes exist, handle concurrency
                if (currId < latestIdNum) {
	                console.debug('Conflict detected: local currId < latestId');
                    if (document.getElementById('windowId').value === windowId) {
                        document.getElementById('curr_id').value = latestId;
	                    console.debug('Window IDs match, checking text');
                        
                        // let decodedLatestText = decodeQueryValue(latestText);
                        let currentTextValue = document.getElementById('thetext').value;

	                    // console.debug('currentTextValue: ' + currentTextValue);
	                    // console.debug('decodedLatestText: ' + latestText);

                        if (! isTextDifferent(latestText, currentTextValue) ){
	                        console.debug('Local and server text match, marking clean');
                            setClean();
                        } else {
	                        console.debug('Text differs - keeping dirty state for user review');
                        }
                    } else {
                        showErrorMessage('Concurrency conflict detected - another window modified this data.');
	                    console.debug('Window IDs do not match - potential concurrent edit');
                    }
                }
            }
        }

        // function log(val){
        //     let logElement = document.getElementById('log');
        //     if (logElement) {
        //         logElement.value = logElement.value + '\n' + new Date().toISOString() + ': ' + val;
        //     }
        // }

        function setClean(){
            document.getElementById('dirty').value = false;
            dirty = false;
            document.getElementById('savebutton').disabled = true;

            // Refresh parent window if available
            if (window.opener && typeof window.opener.callRefreshTabAlerts === 'function') {
                try {
                    window.opener.callRefreshTabAlerts("oscar_scratch");
                } catch (e) {
                    console.warn('Could not call parent refresh:', e);
                }
            }
        }

        function isTextDifferent(scratchPad, returnText) {
            // Normalize both values by decoding URL encoding and HTML entities
            const normalized1 = normalizeText(scratchPad);
            const normalized2 = normalizeText(returnText);
            return normalized1 !== normalized2;
        }

        function normalizeText(text) {
            if (!text) return "";
            if (typeof text !== 'string') return "";

            let normalized = text.trim();

            // First, decode URL encoding if present
            if (normalized.includes('%') || normalized.includes('+')) {
                try {
                    normalized = decodeURIComponent(normalized.replace(/\+/g, " "));
                } catch (e) {
                    console.warn('Could not decode URL value:', text);
                }
            }

            // Then, decode HTML entities
            // Create a temporary element to leverage browser's HTML decoding
            const textarea = document.createElement('textarea');
            textarea.innerHTML = normalized;
            normalized = textarea.value;

            return normalized.trim();
        }

        // Modern replacement for deprecated unescape()
        function decodeQueryValue(str) {
            if (!str) return "";
            if (typeof str !== 'string') {
                console.debug('Warning: decodeQueryValue received non-string: ' + typeof str);
                return "";
            }
            try {
                // Use decodeURIComponent directly - it handles both %20 and + 
                // If + should be treated as space, use URLSearchParams instead
                return decodeURIComponent(str.replace(/\+/g, " "));
            } catch (e) {
                console.debug('Error decoding query value: ' + str + ' - ' + e.message);
                console.error('Error decoding value:', e);
                return "";  // Return empty string instead of original to be consistent
            }
        }

        function showVersion(id) {
	        if (id === "showVersion") {
		        return;
	        }
	        let url = context + "/Scratch.do?method=showVersion&id=" + encodeURIComponent(id);
	        let win = window.open(url, "scratchPadVersion", "width=" +window.innerWidth+ ",height=" +window.innerHeight+ ",toolbar=no, scrollbars=yes");
	        if (win) {
		        win.focus();
	        }
        }
    </script>
    <style>
        .scratch-layout {
            display: flex;
            gap: 15px;
            align-items: flex-start;
        }
        .scratch-sidebar {
            min-width: 200px;
        }
        .scratch-main {
            flex: 1;
            display: flex;
            flex-direction: column;
        }
        .scratch-main textarea {
            width: 100%;
            box-sizing: border-box;
            min-height: 346px;
            background-color: #ffffff;
            box-shadow: 0 2px 4px 0 rgba(38,40,42,0.3);
            border-radius: 4px;
            border: lightgray thin solid;
            padding: 10px;
            flex: 1;
        }
    </style>
</head>

<body>
<div class="container">

    <div class="page-header-bar">
        <h4 class="page-header-title">
            <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" fill="currentColor" viewBox="0 0 16 16" class="page-header-icon">
                <path d="M14.5 3a.5.5 0 0 1 .5.5v9a.5.5 0 0 1-.5.5h-13a.5.5 0 0 1-.5-.5v-9a.5.5 0 0 1 .5-.5zm-13-1A1.5 1.5 0 0 0 0 3.5v9A1.5 1.5 0 0 0 1.5 14h13a1.5 1.5 0 0 0 1.5-1.5v-9A1.5 1.5 0 0 0 14.5 2z"/>
                <path d="M5 8a.5.5 0 0 1 .5-.5h7a.5.5 0 0 1 0 1h-7A.5.5 0 0 1 5 8m0-2.5a.5.5 0 0 1 .5-.5h7a.5.5 0 0 1 0 1h-7a.5.5 0 0 1-.5-.5m0 5a.5.5 0 0 1 .5-.5h7a.5.5 0 0 1 0 1h-7a.5.5 0 0 1-.5-.5m-1-5a.5.5 0 1 1-1 0 .5.5 0 0 1 1 0M4 8a.5.5 0 1 1-1 0 .5.5 0 0 1 1 0m0 2.5a.5.5 0 1 1-1 0 .5.5 0 0 1 1 0"/>
            </svg>
            &nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="ScratchPad.title"/>
            <span class="text-muted" style="font-size: 0.8em; font-weight: normal;">
                &mdash; <%=Encode.forHtmlContent(userfirstname)%> <%=Encode.forHtmlContent(userlastname)%>
            </span>
        </h4>
    </div>

    <div class="scratch-layout">
        <div class="scratch-sidebar">
            <input type="button" class="btn btn-primary mb-2 w-100" onclick="checkScratch('Save button...')" id="savebutton" value="Save" />

            <select class="form-select form-select-sm" onChange="showVersion(this.options[this.selectedIndex].value)">
                <option value="showVersion">Select Version to Display</option>
                <%
                for( ScratchPad scratchPad : dateIdList ) {
                    String strId = scratchPad.getId() + "";
                    Date date = scratchPad.getDateTime();
                %>
                    <option value="<%=Encode.forHtmlAttribute(strId)%>"><%=DateUtils.formatDateTime(date, request.getLocale())%></option>
                <%
                }
                %>
            </select>

            <div id="lastSavedTimestamp" class="text-muted mt-2" style="min-height: 20px;"></div>
        </div>

        <div class="scratch-main" id="mainRight">
            <form id="scratch" action="">
                <input type="hidden" name="providerNo" value="<%=Encode.forHtmlAttribute(user_no)%>" />
                <input type="hidden" name="id" id="curr_id" value="<%=Encode.forHtmlAttribute(id)%>" />
                <input type="hidden" name="windowId" id="windowId" value="<%=String.valueOf(System.nanoTime())%>" />
                <input type="hidden" name="dirty" value=false id="dirty" />
                <textarea name="scratchpad" id="thetext" rows="50"
                    cols="50" oninput="setDirty();" onpaste="setDirty();"><%=Encode.forHtmlContent(text)%></textarea>
            </form>
        </div>
    </div>

<script type="text/javascript">
fixHeightOfTheText(); // fix it first time in.
setClean();
</script>
</div>
</body>
</html>
