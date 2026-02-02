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

--%>
<!DOCTYPE HTML>

<%@ page import="org.owasp.encoder.Encode" %>
<%@ page import="ca.openosp.openo.scratch.ScratchData" %>
<%@ page import="java.util.Map" %>
<%@ page import="ca.openosp.openo.commn.model.ScratchPad" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Date" %>
<%@ page import="ca.openosp.openo.utility.DateUtils" %>
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

    <script type="text/javascript" src="<%=request.getContextPath()%>/library/jquery/jquery-3.6.4.min.js"></script>

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
    <link rel="stylesheet" type="text/css" href="<%=request.getContextPath()%>/library/bootstrap/5.0.2/css/bootstrap.min.css" />

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

              <fmt:setBundle basename="oscarResources"/><fmt:message key="ScratchPad.title"/>
            </h2>
        </div>
          <div class="user-name" >
              <h4><%=Encode.forHtmlContent(userfirstname)%> <%=Encode.forHtmlContent(userlastname)%></h4>
          </div>
    </div>

    <table class="MainTable table-condensed table-borderless" id="scrollNumber1">

	<tr>
		<td class="MainTableLeftColumn" id="tablelle" >
            <input type="button" style="margin-bottom: 8px;" class="btn btn-primary" onclick="checkScratch('Save button...')" id="savebutton" value="save" />

			<select class="form-select" onChange="showVersion(this.options[this.selectedIndex].value)">
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

            <div id="lastSavedTimestamp" style="color: #666; margin-top: 8px; min-height: 20px;"></div>
	    </td>

		<td class="MainTableRightColumn" id="mainRight">
		<form id="scratch" action="">
            <input type="hidden" name="providerNo" value="<%=Encode.forHtmlAttribute(user_no)%>" />
            <input type="hidden" name="id" id="curr_id" value="<%=Encode.forHtmlAttribute(id)%>" />
            <input type="hidden" name="windowId" id="windowId" value="<%=String.valueOf(System.nanoTime())%>" />
            <input type="hidden" name="dirty" value=false id="dirty" />
            <textarea name="scratchpad" id="thetext" rows="50"
			cols="50" oninput="setDirty();" onpaste="setDirty();" ><%=Encode.forHtmlContent(text)%></textarea>

        </form>
		</td>
	</tr>
</table>

<script type="text/javascript">
fixHeightOfTheText(); // fix it first time in.
setClean();
</script>
</div>
</body>
</html>
