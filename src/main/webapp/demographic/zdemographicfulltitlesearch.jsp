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
    Patient Search Interface with Barcode Scanner Support

    Purpose:
    This JSP fragment provides a comprehensive patient search interface with support for multiple
    search modes (name, phone, DOB, address, HIN, chart number, demographic number) and Ontario
    health card barcode scanning capabilities.

    Features:
    - Multi-mode patient search (name, phone, DOB, address, HIN, chart, demographic #)
    - Ontario health card barcode scanner support (%b610054 format)
    - Global keyboard listener for hands-free barcode scanning
    - Real-time DOB format validation and formatting
    - Inactive/All patient search options
    - Most Recent Patients quick access
    - Out-of-domain search (with appropriate security)

    Parameters:
    - search_mode: Type of search (search_name, search_phone, search_dob, etc.)
    - keyword: Search term entered by user
    - fromMessenger: Boolean indicating if called from messenger context

    @since 2006-01-01 (original OSCAR implementation)
    @since 2026-02-09 (CARLOS enhancement: barcode scanner support)
--%>

<%@page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="java.lang.*" %>
<%@page import="io.github.carlos_emr.OscarProperties" %>
<%@page import="org.owasp.encoder.Encode" %>

<%
    boolean fromMessenger = request.getParameter("fromMessenger") == null ? false : (request.getParameter("fromMessenger")).equalsIgnoreCase("true") ? true : false;
    String roleName = session.getAttribute("userrole") + "," + session.getAttribute("user");
%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="/WEB-INF/caisi-tag.tld" prefix="caisi" %>

<script type="application/javascript">
    /**
     * Extracts Health Identification Number (HIN) from Ontario health card barcode format.
     * Ontario health card barcodes follow the %b610054 prefix format with HIN at positions 8-18.
     *
     * @param {string} input - Raw barcode input string
     * @returns {string|null} - Validated 10-digit HIN or null if invalid
     */
    function extractHINFromBarcode(input) {
        const BARCODE_PREFIX = '%b610054';
        const BARCODE_MIN_LENGTH = 18;
        const HIN_START_INDEX = 8;
        const HIN_END_INDEX = 18;

        if (input.indexOf(BARCODE_PREFIX) === 0 && input.length >= BARCODE_MIN_LENGTH) {
            const hin = input.substring(HIN_START_INDEX, HIN_END_INDEX).trim();
            // Validate Ontario HIN format: exactly 10 digits
            if (/^\d{10}$/.test(hin)) {
                return hin;
            }
        }
        return null;
    }

    // Global Barcode Scanner Listener
    // Captures health card swipes even when cursor isn't in search box
    // Only enabled on standalone search page via window.enableGlobalBarcodeSearch flag
    // to prevent unwanted form submissions on edit/add patient pages
    if (window.enableGlobalBarcodeSearch) {
        (function() {
            let barcodeBuffer = '';
            let barcodeTimeout = null;
            const BARCODE_PREFIX = '%b610054';
            const BARCODE_MIN_LENGTH = 18;
            // Maximum buffer size to prevent unbounded growth
            const MAX_BUFFER_LENGTH = 50;
            // Timeout to distinguish scanner (fast) from human typing (slow)
            const TYPING_TIMEOUT = 100;

        /**
         * Submits HIN search and resets barcode buffer state.
         * Centralizes the submit logic to ensure consistent behavior across all barcode detection paths.
         *
         * @param {string} hin - The validated 10-digit Health Identification Number
         * @param {Event} event - The keyboard event to prevent default behavior on
         */
        function submitHIN(hin, event) {
            const searchInput = document.getElementById('keyword');
            const searchMode = document.getElementById('search_mode');

            if (searchInput && searchMode) {
                searchInput.value = hin;
                searchMode.value = 'search_hin';
                document.titlesearch.submit();
            }

            barcodeBuffer = '';
            event.preventDefault();
        }

        document.addEventListener('keydown', function(e) {
            // Ignore if already focused on search input
            if (document.activeElement === document.getElementById('keyword')) {
                return;
            }

            // Only process single printable characters
            if (e.key.length !== 1) {
                return;
            }

            // Clear buffer after typing pause (human typing vs scanner)
            if (barcodeTimeout) {
                clearTimeout(barcodeTimeout);
            }

            // Add character to buffer
            barcodeBuffer += e.key;

            // Prevent unbounded buffer growth
            if (barcodeBuffer.length > MAX_BUFFER_LENGTH) {
                barcodeBuffer = '';
                return;
            }

            // Check for Enter key to finalize barcode scan
            // Many scanners send Enter after the barcode data
            if (e.key === 'Enter' && barcodeBuffer.length >= BARCODE_MIN_LENGTH) {
                const hin = extractHINFromBarcode(barcodeBuffer);

                if (hin) {
                    submitHIN(hin, e);
                    return;
                }
            }

            // Also check if buffer has complete barcode without Enter
            // (for scanners that don't send Enter)
            if (barcodeBuffer.length >= BARCODE_MIN_LENGTH &&
                barcodeBuffer.indexOf(BARCODE_PREFIX) === 0) {

                const hin = extractHINFromBarcode(barcodeBuffer);

                if (hin) {
                    submitHIN(hin, e);
                    return;
                }
            }

            // Reset buffer after short delay (human typing is slower)
            barcodeTimeout = setTimeout(function() {
                barcodeBuffer = '';
            }, TYPING_TIMEOUT);
        });
        })();
    }

    function checkdbstatus() {
        if (document.titlesearch.search_mode.value === 'search_band_number') {
            document.titlesearch.dboperation.value = 'search_status_id';
        } else {
            document.titlesearch.dboperation.value = 'search_titlename';
        }
    }

    function searchInactive() {
        document.titlesearch.ptstatus.value = "inactive";
        if (checkTypeIn()) document.titlesearch.submit();
    }

    function searchAll() {
        document.titlesearch.ptstatus.value = "";
        if (checkTypeIn()) document.titlesearch.submit();
    }

    function searchOutOfDomain() {
        document.titlesearch.outofdomain.value = "true";
        if (checkTypeIn()) document.titlesearch.submit();
    }

    function formatDateInput(input) {
        // Remove any non-digit characters
        let value = input.value.replace(/\D/g, '');

        // Format as YYYY-MM-DD
        if (value.length > 0) {
            // Ensure we only take the first 8 digits
            value = value.substring(0, Math.min(8, value.length));

            // Add hyphens
            if (value.length > 4) {
                value = value.substring(0, 4) + '-' + value.substring(4);
            }
            if (value.length > 7) {
                value = value.substring(0, 7) + '-' + value.substring(7);
            }
        }

        input.value = value;
    }

    function checkTypeIn() {
        const keyword = document.titlesearch.keyword;
        let typeInOK = true;

        // Health Card Barcode Scanner Support (Ontario format)
        // Detects barcode swipe input starting with %b610054 and extracts HIN
        const hin = extractHINFromBarcode(keyword.value);
        if (hin !== null) {
            document.titlesearch.keyword.value = hin;
            document.titlesearch.search_mode.value = 'search_hin';
            return true;
        }

        // Convert name searches to lowercase for consistency
        if (document.titlesearch.search_mode.value === 'search_name') {
            document.titlesearch.keyword.value = keyword.value.toLowerCase();
        }

        // DOB format validation
        if (document.titlesearch.search_mode.value === 'search_dob') {
            // Remove hyphens for validation
            const dobValue = keyword.value.replace(/-/g, '');

            // Check if we have enough digits
            if (dobValue.length > 0 && dobValue.length < 8) {
                alert("Date format must be YYYY-MM-DD");
                typeInOK = false;
            }
        }
        return typeInOK;
    }

</script>
<div class="searchBox">
    <!-- Styled Header with Search Icon -->
    <div class="RowTop header search-header">
        <div class="title">
            <h4 class="search-header-title">
                <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" fill="currentColor" viewBox="0 0 16 16" class="search-header-icon">
                    <path d="M11.742 10.344a6.5 6.5 0 1 0-1.397 1.398h-.001q.044.06.098.115l3.85 3.85a1 1 0 0 0 1.415-1.414l-3.85-3.85a1 1 0 0 0-.115-.1zM12 6.5a5.5 5.5 0 1 1-11 0 5.5 5.5 0 0 1 11 0"/>
                </svg>
                &nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.search.msgSearchPatient"/>
            </h4>
        </div>
    </div>
    <form method="get" name="titlesearch" action="<%=request.getContextPath()%>/demographic/demographiccontrol.jsp"
          onsubmit="return checkTypeIn()">

        <% String searchMode = request.getParameter("search_mode");
            String keyWord = request.getParameter("keyword");
            if (searchMode == null || searchMode.equals("")) {
                searchMode = OscarProperties.getInstance().getProperty("default_search_mode", "search_name");
            }
            if (keyWord == null) {
                keyWord = "";
            }
        %>
        <div class="input-group select-group">
            <select class="form-control input-group-addon" name="search_mode" id="search_mode" onchange="if(this.value === 'search_dob') document.titlesearch.keyword.value = '';">
                <option value="search_name" <%=searchMode.equals("search_name") ? "selected" : ""%>>
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.zdemographicfulltitlesearch.formName"/>
                </option>
                <option value="search_phone" <%=searchMode.equals("search_phone") ? "selected" : ""%>>
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.zdemographicfulltitlesearch.formPhone"/>
                </option>
                <option value="search_dob" <%=searchMode.equals("search_dob") ? "selected" : ""%>>
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.zdemographicfulltitlesearch.formDOB"/>
                </option>
                <option value="search_address" <%=searchMode.equals("search_address") ? "selected" : ""%>>
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.zdemographicfulltitlesearch.formAddr"/>
                </option>
                <option value="search_hin" <%=searchMode.equals("search_hin") ? "selected" : ""%>>
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.zdemographicfulltitlesearch.formHIN"/>
                </option>
                <option value="search_chart_no" <%=searchMode.equals("search_chart_no") ? "selected" : ""%>>
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.zdemographicfulltitlesearch.formChart"/>
                </option>
                <option value="search_demographic_no" <%=searchMode.equals("search_demographic_no") ? "selected" : ""%>>
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.zdemographicfulltitlesearch.formDemographicNo"/>
                </option>
                <oscar:oscarPropertiesCheck value="true" defaultVal="false" property="FIRST_NATIONS_MODULE">
                    <option value="search_band_number" <%=searchMode.equals("search_band_number") ? "selected" : ""%> >
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.zdemographicfulltitlesearch.formBandNumber"/>
                    </option>
                </oscar:oscarPropertiesCheck>
            </select>

            <input class="wideInput form-control" type="search"
                   placeholder="<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.zdemographicfulltitlesearch.msgSearch"/>"
                   NAME="keyword" ID="keyword"
                   aria-label="<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.zdemographicfulltitlesearch.msgSearch"/>"
                   VALUE="<%=Encode.forHtmlAttribute(keyWord)%>" SIZE="17" MAXLENGTH="100"
                   oninput="if(document.titlesearch.search_mode.value === 'search_dob') formatDateInput(this);"
                   onkeyup="if(document.titlesearch.search_mode.value === 'search_dob') formatDateInput(this);">


            <INPUT TYPE="hidden" NAME="orderby" VALUE="last_name, first_name">
            <INPUT TYPE="hidden" NAME="dboperation" VALUE="search_titlename">
            <INPUT TYPE="hidden" NAME="limit1" VALUE="0">
            <INPUT TYPE="hidden" NAME="limit2" VALUE="10">
            <INPUT TYPE="hidden" NAME="displaymode" VALUE="Search">
            <INPUT TYPE="hidden" NAME="ptstatus" VALUE="active">
            <INPUT TYPE="hidden" NAME="fromMessenger" VALUE="<%=fromMessenger%>">
            <INPUT TYPE="hidden" NAME="outofdomain" VALUE="">
            <div class="input-group-btn">
                <INPUT TYPE="SUBMIT" class="rightButton blueButton top btn btn-primary"
                       VALUE="<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.zdemographicfulltitlesearch.msgSearch"/>" SIZE="17"
                       TITLE="<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.zdemographicfulltitlesearch.tooltips.searchActive"/>">


                <INPUT TYPE="button" class="btn btn-secondary" onclick="searchInactive();"
                       TITLE="<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.zdemographicfulltitlesearch.tooltips.searchInactive"/>"
                       VALUE="<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.search.Inactive"/>">

                <INPUT TYPE="button" class="btn btn-secondary" onclick="searchAll();"
                       TITLE="<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.zdemographicfulltitlesearch.tooltips.searchAll"/>"
                       VALUE="<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.search.All"/>">

                <INPUT TYPE="button" class="btn btn-secondary"
                       onclick="try{if(window.opener && !window.opener.closed){window.opener.location.reload();window.close();}else{window.history.back();}}catch(e){window.history.back();}"
                       VALUE="<fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnBack"/>"
                       TITLE="<fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnBack"/>">

                <INPUT TYPE="button" class="btn btn-link"
                       onclick="document.titlesearch.keyword.value='';document.titlesearch.submit();"
                       VALUE="<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicsearchresults.msgMostRecentPatients"/>"
                       TITLE="<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicsearchresults.msgMostRecentPatients"/>">
            </div>

            <security:oscarSec roleName="<%=roleName%>" objectName="_search.outofdomain" rights="r">
                <INPUT TYPE="button" onclick="searchOutOfDomain();"
                       TITLE="<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.zdemographicfulltitlesearch.tooltips.searchOutOfDomain"/>"
                       VALUE="<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.search.OutOfDomain"/>">
            </security:oscarSec>

            <caisi:isModuleLoad moduleName="caisi">
                <input type="button" value="cancel"
                       onclick="location.href='${request.contextPath}/PMmodule/ProviderInfo.do'">
            </caisi:isModuleLoad>
        </div>

    </form>
</div>
