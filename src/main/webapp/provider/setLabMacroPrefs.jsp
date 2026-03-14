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
    ================================================================================
    setLabMacroPrefs.jsp
    ================================================================================
    Purpose:
        Provides a user-friendly Bootstrap 5 interface for managing lab macro
        preferences in CARLOS EMR. Lab macros are quick-action templates that
        providers can configure to auto-populate lab result acknowledgments
        with comments and assigned ticklers for follow-up reminders.

    Key Features:
        - Form-based UI for creating, editing, and deleting lab macros
        - JSON storage backend for macro definitions and settings
        - Optional tickler (follow-up reminder) assignment with configurable delay
        - Support for time-based follow-up intervals (days, weeks, months, years)
        - Raw JSON editor toggle for advanced users who need direct data access
        - Full internationalization support (English, Portuguese Brazilian)
        - CSRF protection provided by OWASP CSRFGuard (automatic token injection)
        - OWASP XSS encoding for all user-controlled data
        - Server-side JSON validation to prevent malformed data persistence

    Architecture:
        - Page loads existing macros from UserProperty.LAB_MACRO_JSON via SpringUtils
        - User fills form fields or edits raw JSON directly
        - JavaScript assembleJSON() function builds JSON from form or passes through raw JSON
        - Form submission calls setProviderStaleDate.do?method=saveLabMacroPrefs
        - Action validates JSON and persists to UserProperty table
        - JSP displays success or error status on form return

    Security:
        - Requires authenticated provider session (checks session.user)
        - Authorization checked in ProviderProperty2Action.saveLabMacroPrefs() for _lab WRITE privilege
        - All outputs escaped with OWASP Encoder (Encode.forHtmlAttribute, Encode.forHtml)
        - JSON parsing errors logged server-side and displayed to user with corrective instructions
        - Tickler assignments limited to active providers in system

    JSON Schema Example:
        timeUnits values: 1=days, 7=weeks, 30=months, 365=years

        [
            {
                "name": "Abnormal",
                "acknowledge": {
                    "comment": "Reviewed with patient"
                },
                "tickler": {
                    "taskAssignedTo": "P001",
                    "message": "Follow up on abnormal result",
                    "quantity": 7,
                    "timeUnits": 1
                },
                "closeOnSuccess": true
            }
        ]

    Status Handling:
        - "success": Displayed when macros successfully saved
        - "error": Displayed when submitted JSON fails validation
        - Parse errors during page load show alert-danger with instruction to use raw JSON editor

    Issue History:
        - 2019-07-06: Original feature implementation
        - 2026-02-13: Bootstrap 5 UI rewrite with improved UX
        - 2026-02-17: Server-side JSON validation, improved error handling

    @since 2019-07-06 (Bootstrap UI rewrite 2026-02-13)
--%>

<%@page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@page import="java.util.*" %>
<%@page import="org.apache.commons.lang3.StringUtils"%>
<%@page import="io.github.carlos_emr.carlos.utility.SpringUtils"%>
<%@page import="io.github.carlos_emr.carlos.commn.model.Provider"%>
<%@page import="io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao"%>
<%@page import="io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO"%>
<%@page import="io.github.carlos_emr.carlos.commn.model.UserProperty"%>
<%@page import="io.github.carlos_emr.carlos.utility.MiscUtils"%>

<%@page import="java.io.IOException"%>
<%@page import="com.fasterxml.jackson.databind.JsonNode"%>
<%@page import="com.fasterxml.jackson.databind.ObjectMapper"%>

<%@page import="org.owasp.encoder.Encode"%>

<%@taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt"%>
<fmt:setBundle basename="oscarResources"/>
<%-- Capture i18n message into page-scope variable for safe use in JavaScript with OWASP encoding --%>
<fmt:message key="provider.labMacroPrefs.confirmDeleteAll" var="confirmDeleteAllMsg"/>

<%
// ============================================================================
// INITIALIZATION & SECURITY CHECK
// ============================================================================
// Retrieve the logged-in provider from session. This JSP requires an
// authenticated provider session - if not present, respond with 401.
String curProviderNo = (String) session.getAttribute("user");

if (curProviderNo == null || curProviderNo.isEmpty()) {
    response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
    return;
}

// ============================================================================
// DATA LOADING
// ============================================================================
// Load the list of active providers for the tickler assignment dropdown.
// This populates the "Assigned To" field where users can select which
// provider should receive the tickler follow-up reminder.
ProviderDao providerDao = SpringUtils.getBean(ProviderDao.class);
List<Provider> providerList = providerDao.getActiveProviders();

// Jackson ObjectMapper for parsing and validating lab macro JSON.
// Each macro stored in UserProperty is a JSON array of macro objects.
ObjectMapper mapper = new ObjectMapper();

// Load the current user's lab macro preferences from the database.
// UserProperty.LAB_MACRO_JSON is the key used to store macros for this provider.
// Returns null if no macros have been saved yet.
UserPropertyDAO upDao = SpringUtils.getBean(UserPropertyDAO.class);
UserProperty up = upDao.getProp(curProviderNo, UserProperty.LAB_MACRO_JSON);

%>
<!DOCTYPE HTML>
<html>
<head>

<%@ include file="/includes/global-head.jspf" %>
<title><fmt:message key="provider.labMacroPrefs.msgPrefs"/></title>

<script>
// ============================================================================
// CLIENT-SIDE FORM HANDLING & JSON ASSEMBLY
// ============================================================================

// Localized confirmation message when user attempts to delete all macros.
// Encoded with OWASP Encode.forJavaScript() to prevent XSS from translation strings.
var MSG_CONFIRM_DELETE_ALL = '<%=Encode.forJavaScript((String) pageContext.getAttribute("confirmDeleteAllMsg"))%>';

/**
 * assembleJSON()
 *
 * Purpose:
 *     Converts form-based macro entries into JSON for submission to server.
 *     Called on form submission via onsubmit="return assembleJSON();" to validate
 *     and prepare data before posting to ProviderProperty2Action.saveLabMacroPrefs().
 *
 * Logic:
 *     1. If raw JSON editor is visible (not hidden), bypass form assembly and return
 *        true immediately. This allows users to submit hand-edited JSON directly.
 *     2. Otherwise, iterate through visible macro_* form sections and extract field values.
 *     3. For each macro with a name, build a JSON object containing:
 *        - name: macro template name (e.g., "Abnormal", "Critical")
 *        - acknowledge: comment to auto-populate in result acknowledgment
 *        - tickler (optional): follow-up reminder with assigned provider and message
 *        - closeOnSuccess: boolean flag to auto-close result display on confirm
 *     4. Convert macro array to JSON string (empty string if no macros).
 *     5. If resulting JSON is empty and macros previously existed, show delete confirmation.
 *     6. Verify macroJSON textarea exists (DOM integrity check).
 *     7. Populate hidden textarea with assembled JSON and allow form submission.
 *
 * Returns:
 *     true if form should submit, false if user cancels or error detected
 *
 * Security Notes:
 *     - This function does NOT escape JSON output; OWASP encoding is server-side only
 *     - Form values are collected as-is; server validates JSON structure
 *     - Empty macro_* divs (deleted via style.display='none') are skipped automatically
 *
 * @returns {boolean} true to allow form submission, false to prevent
 */
function assembleJSON() {
    // If raw JSON editor is visible, skip form assembly.
    // User is editing JSON directly, so we pass it through unchanged to the server.
    var rawPanel = document.getElementById('raw');
    if (rawPanel && rawPanel.style.display !== 'none') {
        return true;  // Allow form submission with raw JSON textarea value
    }

    // Array to collect macro objects from visible form entries.
    let macros = [];

    // Query all form sections for macros. Elements are identified by id="macro_N"
    // where N is an index (e.g., macro_0, macro_1, macro_new).
    const elements = document.querySelectorAll('[id^="macro_"]');

    elements.forEach(el => {
        // Skip deleted macro rows (marked display:none by delete button onclick).
        // Only process macros that are currently visible to the user.
        if (window.getComputedStyle(el).display !== 'none') {
            // Extract the numeric or text suffix from id (e.g., "0" from "macro_0").
            let suffix = el.id.split('_')[1];

            // Retrieve the macro name field. Name is required to include macro in JSON.
            let nameField = document.getElementById('name_' + suffix);

            // Only include this macro if name is provided (non-empty).
            if (nameField && nameField.value.length > 0) {
                // Retrieve optional tickler/acknowledgment fields.
                let commentField = document.getElementById('comment_' + suffix);
                let ticklerTo = document.getElementById('ticklerTo_' + suffix);
                let messageField = document.getElementById('message_' + suffix);
                let quantityField = document.getElementById('quantity_' + suffix);
                let timeUnitsField = document.getElementById('timeUnits_' + suffix);

                // Build macro object with required and optional properties.
                let macroObj = {
                    name: nameField.value,
                    // Acknowledge comment to display when macro is applied to lab result
                    acknowledge: {
                        comment: commentField ? commentField.value : ''
                    },
                    // Auto-close the lab result display after acknowledgment
                    closeOnSuccess: true
                };

                // If tickler assignment is specified, add tickler configuration.
                if (ticklerTo && ticklerTo.value.length > 0) {
                    macroObj.tickler = {
                        taskAssignedTo: ticklerTo.value,  // Provider ID to receive tickler
                        message: messageField ? messageField.value : ''
                    };

                    // If quantity > 0, add scheduling information for follow-up reminder.
                    // quantity: how many units (converted to integer)
                    // timeUnits: 1=days, 7=weeks, 30=months, 365=years
                    if (quantityField && parseInt(quantityField.value) > 0) {
                        macroObj.tickler.quantity = parseInt(quantityField.value);
                        macroObj.tickler.timeUnits = timeUnitsField ? parseInt(timeUnitsField.value) : 1;
                    }
                }

                macros.push(macroObj);
            }
        }
    });

    // Convert macro array to JSON string, or empty string if no macros.
    let jsonStr = macros.length > 0 ? JSON.stringify(macros) : '';

    // If user is deleting ALL macros (empty JSON) and the form contains multiple macro_* divs,
    // show confirmation dialog. This prevents accidental data loss.
    // Condition: (1) assembled JSON is empty AND (2) more than one macro_* div exists in DOM
    if (jsonStr === '' && document.querySelectorAll('[id^="macro_"]').length > 1) {
        if (!confirm(MSG_CONFIRM_DELETE_ALL)) {
            // User declined confirmation, prevent form submission
            return false;
        }
    }

    // Locate the hidden textarea that will be submitted to the server.
    // This textarea contains the JSON and is sent as form field "labMacroJSON.value".
    let jsonOutput = document.getElementById('macroJSON');
    if (!jsonOutput) {
        // DOM integrity check: textarea should always exist. If not, page is corrupted.
        alert('Error: Could not find the JSON output field. Please reload the page.');
        return false;
    }

    jsonOutput.value = jsonStr;
    return true;
}

/**
 * toggleMe(el)
 *
 * Purpose:
 *     Simple utility to toggle visibility of an element between hidden and visible.
 *     Used to show/hide the raw JSON editor panel when user clicks "Show macro JSON" link.
 *
 * @param {HTMLElement} el - DOM element to toggle
 */
function toggleMe(el){
    el.style.display = (el.style.display === 'none') ? 'block' : 'none';
}

</script>

</head>
<body>

<div class="card mb-3">
    <div class="card-header bg-light">
        <div class="row">
            <div class="col-sm-6">
                <h4 class="mb-0"><fmt:message key="provider.labMacroPrefs.msgPrefs" /></h4>
            </div>
            <div class="col-sm-6 text-center">
                <fmt:message key="provider.labMacroPrefs.title" />
            </div>
        </div>
    </div>
</div>

<form name="labMacroPrefsForm" method="post" action="${pageContext.request.contextPath}/setProviderStaleDate.do" onsubmit="return assembleJSON();">
<input type="hidden" name="method" value="saveLabMacroPrefs">
<div class="container mt-3">

<%
// ============================================================================
// STATUS MESSAGE DISPLAY
// ============================================================================
// Display success or error messages based on action result.
// - "success": Macros were successfully saved
// - "error": Submitted JSON failed validation
String status = (String) request.getAttribute("status");

// Check if the server preserved submitted JSON on validation error.
// Used to: (1) auto-show raw editor with user's input, (2) suppress stale form fields.
String submittedJSON = (String) request.getAttribute("submittedJSON");

if ("success".equals(status)) {
%>
    <div class="alert alert-success"><fmt:message key="provider.labMacroPrefs.msgSuccess" /></div>
<%
} else if ("error".equals(status)) {
%>
    <div class="alert alert-danger"><fmt:message key="provider.labMacroPrefs.msgSaveError" /></div>
<%
}

// ============================================================================
// MACRO RENDERING LOOP
// ============================================================================
// Load and display existing macros from the database. If no macros exist,
// this block is skipped and only the "new" macro form is shown.
// If JSON parsing fails, an error message is displayed to the user.
// On validation error (submittedJSON present), suppress form fields to avoid
// showing stale database data that contradicts the raw editor content.
if (submittedJSON == null && up != null && !StringUtils.isEmpty(up.getValue())) {
    try {
        // Parse the stored JSON string into a JsonNode tree for inspection.
        // The macro JSON is an array of macro objects.
        JsonNode macros = mapper.readTree(up.getValue());
        if (macros != null && macros.isArray()) {
            // Counter for form element IDs. Each macro gets unique IDs based on this index.
            int x = 0;

            // Iterate through each macro in the array and render form row.
            for (JsonNode macro : macros) {
                // ============================================================
                // FIELD EXTRACTION
                // ============================================================
                // Extract macro properties from JSON, providing safe defaults
                // if properties are missing. This ensures the form always renders
                // even if stored JSON is incomplete or partially corrupted.

                // Macro name: identifies the template (e.g., "Abnormal", "Critical")
                // Uses macro.path() which is null-safe (returns MissingNode, never null, so .asText("") provides the default)
                String name = macro.path("name").asText("");
                String comment = "";
                String ticklerTo = "";
                String message = "";
                String quantity = "0";
                String timeUnits = "1";

                // Acknowledge object: contains comment to display in result acknowledgment
                JsonNode acknowledge = macro.path("acknowledge");
                if (!acknowledge.isMissingNode()) {
                    comment = acknowledge.path("comment").asText("");
                }

                // Tickler object (optional): specifies follow-up reminder configuration
                // Contains: taskAssignedTo (provider ID), message, quantity, timeUnits
                // This is an optional feature; macros can exist without tickler assignments.
                JsonNode tickler = macro.path("tickler");
                if (!tickler.isMissingNode()) {
                    // Provider ID who should receive the tickler task
                    ticklerTo = tickler.path("taskAssignedTo").asText("");
                    // Task message for the tickler (visible to assigned provider)
                    message = tickler.path("message").asText("");
                    // Scheduling: only if both quantity and timeUnits are present
                    if (tickler.has("quantity") && tickler.has("timeUnits")) {
                        quantity = tickler.path("quantity").asText("");
                        timeUnits = tickler.path("timeUnits").asText("");
                    }
                }

%>

 <div class="mb-3 row" id="macro_<%=x%>">
    <div class="col-sm-2">
        <label for="name_<%=x%>"><fmt:message key="global.macro" /></label><br><input type="text" id="name_<%=x%>" class="form-control form-control-sm" placeholder="<fmt:message key="name" />" value="<%=Encode.forHtmlAttribute(name)%>">
    </div>
    <div class="col-sm-3">
        <label for="comment_<%=x%>"><fmt:message key="provider.appointmentprovideradminmonth.btnLab" />&nbsp;<fmt:message key="oscarMDS.segmentDisplay.btnComment" /></label><br><input type="text" id="comment_<%=x%>" class="form-control form-control-sm" value="<%=Encode.forHtmlAttribute(comment)%>" placeholder="<fmt:message key="oscarMDS.segmentDisplay.btnComment" />">
    </div>
    <div class="col-sm-2">
        <label for="message_<%=x%>"><fmt:message key="global.tickler" /></label><br><input type="text" id="message_<%=x%>" class="form-control form-control-sm w-100" placeholder="<fmt:message key="tickler.ticklerMain.msgMessage" />" value="<%=Encode.forHtmlAttribute(message)%>">
    </div>
    <div class="col-sm-2">
        <label for="ticklerTo_<%=x%>"><fmt:message key="tickler.ticklerMain.msgAssignedTo" /></label><br><select id="ticklerTo_<%=x%>" name="ticklerTo_<%=x%>" class="form-control form-control-sm w-100">
            <option value=""<%=(ticklerTo.equals("") ? " selected=\"selected\"" : "")%>>-</option>
            <%for (Provider p : providerList) {%>
            <option value="<%=Encode.forHtmlAttribute(p.getProviderNo())%>"<%=(ticklerTo.equals(p.getProviderNo()) ? " selected=\"selected\"" : "")%>><%=Encode.forHtml(p.getFullName())%></option>
            <%}%>
        </select>
    </div>
    <div class="col-sm-3">
        <label for="quantity_<%=x%>"><fmt:message key="tickler.ticklerMain.msgDate" /></label><div style="display: flex;"><input type="number" id="quantity_<%=x%>" class="form-control form-control-sm" style="width:50px;" value="<%=Encode.forHtmlAttribute(quantity)%>"><select id="timeUnits_<%=x%>" class="form-control form-control-sm" style="width:80px;">
            <option value="1"<%=(timeUnits.equals("1") ? " selected=\"selected\"" : "")%>><fmt:message key="global.days" /></option>
            <option value="7"<%=(timeUnits.equals("7") ? " selected=\"selected\"" : "")%>><fmt:message key="global.weeks" /></option>
            <option value="30"<%=(timeUnits.equals("30") ? " selected=\"selected\"" : "")%>><fmt:message key="global.months" /></option>
            <option value="365"<%=(timeUnits.equals("365") ? " selected=\"selected\"" : "")%>><fmt:message key="global.years" /></option>
        </select>
<input type="button" id="delete_<%=x%>" class="btn btn-link btn-sm" value="<fmt:message key="global.btnDelete" />" onclick="document.getElementById('macro_<%=x%>').style.display = 'none';"></div>
    </div>

 </div>

<%
                x++;
            }
        }
    } catch (IOException e) {
        MiscUtils.getLogger().error("Invalid JSON for lab macros", e);
%>
    <div class="alert alert-danger"><fmt:message key="provider.labMacroPrefs.msgParseError" /></div>
<%
    }
}
%>

 <div class="mb-3 row" id="macro_new">
    <div class="col-sm-2">
        <label for="name_new"><fmt:message key="global.macro" /></label><br><input type="text" id="name_new" class="form-control form-control-sm" placeholder="<fmt:message key="name" />" value="">
    </div>
    <div class="col-sm-3">
        <label for="comment_new"><fmt:message key="provider.appointmentprovideradminmonth.btnLab" />&nbsp;<fmt:message key="oscarMDS.segmentDisplay.btnComment" /></label><br><input type="text" id="comment_new" class="form-control form-control-sm" value="" placeholder="<fmt:message key="oscarMDS.segmentDisplay.btnComment" />">
    </div>
    <div class="col-sm-2">
        <label for="message_new"><fmt:message key="global.tickler" /></label><br><input type="text" id="message_new" class="form-control form-control-sm w-100" placeholder="<fmt:message key="tickler.ticklerMain.msgMessage" />" value="">
    </div>
    <div class="col-sm-2">
        <label for="ticklerTo_new"><fmt:message key="tickler.ticklerMain.msgAssignedTo" /></label><br><select id="ticklerTo_new" name="ticklerTo_new" class="form-control form-control-sm w-100">
            <option value="" selected="selected">-</option>
            <%for (Provider p : providerList) {%>
            <option value="<%=Encode.forHtmlAttribute(p.getProviderNo())%>"><%=Encode.forHtml(p.getFullName())%></option>
            <%}%>
        </select>
    </div>
    <div class="col-sm-3">
        <label for="quantity_new"><fmt:message key="tickler.ticklerMain.msgDate" /></label><div style="display: flex;"><input type="number" id="quantity_new" class="form-control form-control-sm" style="width:50px;" value="0"><select id="timeUnits_new" class="form-control form-control-sm" style="width:80px;">
            <option value="1"><fmt:message key="global.days" /></option>
            <option value="7"><fmt:message key="global.weeks" /></option>
            <option value="30"><fmt:message key="global.months" /></option>
            <option value="365"><fmt:message key="global.years" /></option>
        </select></div>
    </div>
</div>

<div class="mb-3 row mt-3">
    <div class="col-sm-5 offset-sm-1">
        <input type="submit" class="btn btn-primary" value="<fmt:message key="global.btnSave" />"/>
        <input type="button" class="btn btn-secondary" value="<fmt:message key="global.btnClose" />" onclick="window.close();"/>
        <a href="javascript:void(0);" onclick="toggleMe(document.getElementById('raw'));" class="btn btn-link btn-sm"><fmt:message key="provider.labMacroPrefs.showJSON" /></a>
    </div>
</div>

<%
// If the server returned submitted JSON (on validation error), show that instead of
// the database value so the user can see and correct what they submitted.
String rawJsonValue = (submittedJSON != null) ? submittedJSON : ((up != null && up.getValue() != null) ? up.getValue() : "");
// Auto-show raw JSON editor on validation error so user can fix their input.
String rawPanelStyle = (submittedJSON != null) ? "display:block;" : "display:none;";
%>
<div class="mb-3 row" style="<%=rawPanelStyle%>" id="raw">
    <textarea name="labMacroJSON.value" id="macroJSON" style="width:80%;height:80%" rows="25"><%=Encode.forHtml(rawJsonValue)%></textarea>
    <input type="submit" class="btn btn-secondary" value="<fmt:message key="global.btnSave" />" />
</div>
</div>
</form>
</body>
</html>
