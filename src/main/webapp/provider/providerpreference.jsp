<%--

    Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
    Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.

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

    Originally written for the Department of Family Medicine, McMaster University.
    Now maintained by the CARLOS EMR Project.
    https://github.com/carlos-emr/carlos

--%>
<%--
    Provider Preferences - Consolidated Single-Page View

    Displays all provider preferences in a single Bootstrap 5 accordion layout,
    themed to match the CARLOS EMR schedule page (navy blue #486ebd header, clean
    clinical styling). Uses FontAwesome icons for visual clarity.

    Previously, most settings required navigating to many separate sub-pages via
    setProviderStaleDate.do. Now they are all inlined with a single Save button.
    Only items that truly require a separate page (password change, signature edit,
    printer setup, etc.) remain as external links.

    Data flow:
      - Reads: ProviderPreference entity + UserProperty map (bulk-loaded)
      - Saves: POST to providerupdatepreference.jsp which calls both
               ProviderPreferencesUIBean.updateOrCreateProviderPreferences() and
               ProviderPropertyAction.updateOrCreateProviderProperties()
      - One field auto-saves via AJAX: rxInteractionWarningLevel

    @since 2002 (original), redesigned 2026-02-14 for consolidated single-page view
--%>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>
<%@ page errorPage="/errorpage.jsp" %>

<%@ page import="java.util.*" %>

<%@ page import="io.github.carlos_emr.CarlosProperties" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.CtlBillingServiceDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.EForm" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.EncounterForm" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.ProviderPreference" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.UserProperty" %>
<%@ page import="io.github.carlos_emr.carlos.eform.EFormUtil" %>
<%@ page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="io.github.carlos_emr.carlos.utility.MiscUtils" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.web.PrescriptionQrCodeUIBean" %>
<%@ page import="io.github.carlos_emr.carlos.web.admin.ProviderPreferencesUIBean" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%!
    // DAOs declared at class level -- thread-safe Spring singletons shared across all requests
    CtlBillingServiceDao ctlBillingServiceDao = SpringUtils.getBean(CtlBillingServiceDao.class);
    UserPropertyDAO propertyDao = SpringUtils.getBean(UserPropertyDAO.class);
%>
<%
    // ── Authentication & provider context ──────────────────────────────────
    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
    if (loggedInInfo == null) {
        response.sendRedirect(request.getContextPath() + "/logout.jsp");
        return;
    }
    String providerNo = loggedInInfo.getLoggedInProviderNo();
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");

    // ── Load the main ProviderPreference entity (schedule, billing) ──
    ProviderPreference providerPreference = ProviderPreferencesUIBean.getProviderPreference(providerNo);
    if (providerPreference == null) {
        providerPreference = new ProviderPreference();
    }

    // Schedule fields - use request params as override if present (e.g. from redirect).
    // ProviderPreference initializes defaults (startHour=8, endHour=18, everyMin=15) but
    // getters may return null if loaded from a database row with NULL columns.
    // IMPORTANT: Validate request parameters before using them to prevent malformed values.
    String startHourParam = request.getParameter("start_hour");
    String startHour;
    if (startHourParam != null && startHourParam.matches("^(?:[0-9]|1[0-9]|2[0-3])$")) {
        // Valid hour in range 0-23
        startHour = startHourParam;
    } else {
        startHour = String.valueOf(providerPreference.getStartHour() != null ? providerPreference.getStartHour() : 8);
    }

    String endHourParam = request.getParameter("end_hour");
    String endHour;
    if (endHourParam != null && endHourParam.matches("^(?:[0-9]|1[0-9]|2[0-3])$")) {
        // Valid hour in range 0-23
        endHour = endHourParam;
    } else {
        endHour = String.valueOf(providerPreference.getEndHour() != null ? providerPreference.getEndHour() : 18);
    }

    String everyMinParam = request.getParameter("every_min");
    String everyMin;
    if (everyMinParam != null && everyMinParam.matches("^[1-9][0-9]*$")) {
        // Valid positive integer for minutes
        everyMin = everyMinParam;
    } else {
        everyMin = String.valueOf(providerPreference.getEveryMin() != null ? providerPreference.getEveryMin() : 15);
    }

    String myGroupNoParam = request.getParameter("mygroup_no");
    String myGroupNo;
    if (myGroupNoParam != null && myGroupNoParam.matches("^[0-9]+$")) {
        // Valid numeric group number
        myGroupNo = myGroupNoParam;
    } else {
        myGroupNo = providerPreference.getMyGroupNo();
    }

    // ── Bulk-load all UserProperty values into a map for efficient access ──
    Map<String, String> props = propertyDao.getProviderPropertiesAsMap(providerNo);

    // Prescription preferences
    String rxPageSize = props.getOrDefault(UserProperty.RX_PAGE_SIZE, "");
    boolean rxUseRx3 = "yes".equalsIgnoreCase(props.getOrDefault(UserProperty.RX_USE_RX3, "no"));
    boolean rxShowDOB = "yes".equalsIgnoreCase(props.getOrDefault(UserProperty.RX_SHOW_PATIENT_DOB, "no"));
    String rxDefaultQty = props.getOrDefault(UserProperty.RX_DEFAULT_QUANTITY, "");
    if (rxDefaultQty.isEmpty()) {
        // Fallback: the action historically stored under an alternate key
        rxDefaultQty = props.getOrDefault("rxDefaultQuantityProperty", "");
    }
    boolean qrChecked = PrescriptionQrCodeUIBean.isPrescriptionQrCodeEnabledForProvider(providerNo);
    String warningLevel = props.getOrDefault("rxInteractionWarningLevel", "0");

    // Clinical preferences
    String defaultSex = props.getOrDefault(UserProperty.DEFAULT_SEX, "");
    String hcType = props.getOrDefault(UserProperty.HC_TYPE, "");
    boolean cppSingleLine = "yes".equalsIgnoreCase(props.getOrDefault(UserProperty.CPP_SINGLE_LINE, "no"));
    String staleNoteDate = props.getOrDefault(UserProperty.STALE_NOTEDATE, "A");
    String staleFormat = props.getOrDefault(UserProperty.STALE_FORMAT, "No");

    // Consultation preferences
    String consultCutoff = props.getOrDefault(UserProperty.CONSULTATION_TIME_PERIOD_WARNING, "");
    String consultTeam = props.getOrDefault(UserProperty.CONSULTATION_TEAM_WARNING, "");
    String workloadMgmt = props.getOrDefault(UserProperty.WORKLOAD_MANAGEMENT, "");
    String consultPasteFmt = props.getOrDefault(UserProperty.CONSULTATION_REQ_PASTE_FMT, "");
    String eformGroup = props.getOrDefault(UserProperty.EFORM_FAVOURITE_GROUP, "");

    // Lab & messaging preferences
    boolean labAckComment = "yes".equalsIgnoreCase(props.getOrDefault(UserProperty.LAB_ACK_COMMENT, "no"));
    String patientNameLen = props.getOrDefault(UserProperty.PATIENT_NAME_LENGTH, "");
    String displayDocAs = props.getOrDefault(UserProperty.DISPLAY_DOCUMENT_AS, "");
    boolean eDocInReport = "yes".equalsIgnoreCase(
            props.getOrDefault(UserProperty.EDOC_BROWSER_IN_DOCUMENT_REPORT, "no"));
    boolean eDocInMaster = "yes".equalsIgnoreCase(
            props.getOrDefault(UserProperty.EDOC_BROWSER_IN_MASTER_FILE, "no"));

    // Encounter window preferences
    String encWinWidth = props.getOrDefault("encounterWindowWidth", "");
    String encWinHeight = props.getOrDefault("encounterWindowHeight", "");
    boolean encWinMax = "yes".equalsIgnoreCase(props.getOrDefault("encounterWindowMaximize", "no"));
    boolean encOpenInTab = "yes".equalsIgnoreCase(props.getOrDefault(UserProperty.ENCOUNTER_OPEN_IN_TAB, "no"));
    String quickChartSize = props.getOrDefault("quickChartSize", "");

    // Contact info (used on prescriptions and consult letters)
    String rxAddress = props.getOrDefault("rxAddress", "");
    String rxCity = props.getOrDefault("rxCity", "");
    String rxProvince = props.getOrDefault("rxProvince", "");
    String rxPostal = props.getOrDefault("rxPostal", "");
    String rxPhone = props.getOrDefault("rxPhone", "");
    String faxNum = props.getOrDefault("faxnumber", "");

    // Display preferences
    String colour = props.getOrDefault("ProviderColour", "");
    boolean dashboardShare = "yes".equalsIgnoreCase(props.getOrDefault(UserProperty.DASHBOARD_SHARE, "no"));

    // Appointment card preferences
    String apptCardName = props.getOrDefault("appointmentCardName", "");
    String apptCardPhone = props.getOrDefault("appointmentCardPhone", "");
    String apptCardFax = props.getOrDefault("appointmentCardFax", "");

    // Signature stamp
    String consultSigValue = props.getOrDefault(UserProperty.PROVIDER_CONSULT_SIGNATURE, "");
    boolean hasConsultSignature = !consultSigValue.isEmpty();

    // Prevention warning preferences (use "true"/"false" unlike most prefs)
    boolean prevSSO = "true".equalsIgnoreCase(
            props.getOrDefault(UserProperty.PREVENTION_SSO_WARNING, "false"));
    boolean prevISPA = "true".equalsIgnoreCase(
            props.getOrDefault(UserProperty.PREVENTION_ISPA_WARNING, "false"));
    boolean prevNonISPA = "true".equalsIgnoreCase(
            props.getOrDefault(UserProperty.PREVENTION_NON_ISPA_WARNING, "false"));

    // Schedule week view weekends (uses boolean parsing, not yes/no)
    boolean weekendsEnabled = true;
    UserProperty showWeekendsProp = propertyDao.getProp(providerNo, UserProperty.SCHEDULE_WEEK_VIEW_WEEKENDS);
    if (showWeekendsProp != null) {
        weekendsEnabled = Boolean.parseBoolean(showWeekendsProp.getValue());
    }

    // eForm groups for the favourite group dropdown
    ArrayList<HashMap<String, String>> eformGroups = EFormUtil.getEFormGroups();

%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <%@ include file="/includes/global-head.jspf" %>
    <c:set var="ctx" value="${pageContext.request.contextPath}"/>
    <title><fmt:message key="provider.providerpreference.pageTitle"/></title>

    <style>
        /* ─── Colour palette matched to CARLOS schedule page ─── */
        :root {
            --carlos-navy: #486ebd;          /* Primary brand colour from schedule header */
            --carlos-navy-dark: #3a5a9e;     /* Darker shade for hover/active states */
            --carlos-navy-light: #5a7ec8;    /* Lighter shade for gradients */
            --carlos-blue: #3EA4E1;          /* Accent blue from schedule time slots */
            --carlos-teal: #00A488;          /* Teal accent from schedule */
            --carlos-text: #00283c;          /* Dark blue text from schedule links */
            --carlos-text-secondary: #475569;
            --carlos-bg: #f0f4f8;            /* Light cool grey page background */
            --carlos-input-bg: #F4EaD7;      /* Cream input background from schedule */
            --carlos-input-border: #0097cf;  /* Cyan border from schedule inputs */
            --carlos-border: #d5dce6;
            --carlos-card-bg: #ffffff;
        }

        /* ─── Base layout ─── */
        body {
            background: var(--carlos-bg);
            font-family: "Helvetica Neue", Helvetica, Arial, sans-serif;
            font-size: 13px;
            color: var(--carlos-text);
            margin: 0;
            padding: 0;
        }

        /* ─── Sticky header bar ─── */
        .pref-header {
            background: var(--carlos-navy);
            color: #fff;
            padding: 10px 20px;
            position: sticky;
            top: 0;
            z-index: 100;
            display: flex;
            align-items: center;
            justify-content: space-between;
            box-shadow: 0 2px 4px rgba(0,0,0,.15);
        }
        .pref-header h1 {
            font-size: 15px;
            margin: 0;
            font-weight: 600;
            letter-spacing: .3px;
        }
        .pref-header h1 i { margin-right: 8px; opacity: .85; }
        .pref-header .header-hint {
            font-size: 11px;
            opacity: .7;
        }

        /* ─── Main scrollable body ─── */
        .pref-body {
            padding: 12px 14px 80px 14px;
            max-width: 100%;
        }

        /* ─── Accordion styling (matching schedule page feel) ─── */
        .accordion-item {
            border: 1px solid var(--carlos-border);
            margin-bottom: 6px;
            border-radius: 6px !important;
            overflow: hidden;
        }
        .accordion-button {
            font-size: 13px;
            font-weight: 600;
            padding: 10px 16px;
            color: var(--carlos-text);
            background: var(--carlos-card-bg);
        }
        .accordion-button i.section-icon {
            width: 22px;
            text-align: center;
            margin-right: 10px;
            color: var(--carlos-navy);
            font-size: 14px;
        }
        .accordion-button:not(.collapsed) {
            background: #e8eef7;
            color: var(--carlos-navy-dark);
            box-shadow: none;
        }
        .accordion-button:not(.collapsed) i.section-icon {
            color: var(--carlos-navy-dark);
        }
        .accordion-button:focus {
            box-shadow: 0 0 0 2px rgba(72, 110, 189, .25);
        }
        .accordion-button::after {
            /* Adjust chevron icon to approximate brand colour */
            filter: hue-rotate(200deg) brightness(0.7);
        }
        .accordion-body {
            padding: 10px 16px 14px 16px;
            background: var(--carlos-card-bg);
        }

        /* ─── Preference rows (label + value pairs) ─── */
        .pref-row {
            display: flex;
            align-items: center;
            padding: 6px 0;
            border-bottom: 1px solid #f0f3f7;
            gap: 10px;
        }
        .pref-row:last-child { border-bottom: 0; }
        .pref-row.align-top { align-items: flex-start; padding-top: 8px; }

        .pref-label {
            flex: 0 0 42%;
            font-weight: 500;
            color: var(--carlos-text);
            font-size: 12px;
            line-height: 1.4;
        }
        .pref-value { flex: 1; }

        /* ─── Form inputs (schedule page cream style) ─── */
        .pref-input {
            font-size: 12px;
            padding: 4px 8px;
            background: var(--carlos-input-bg);
            border: 1px solid var(--carlos-input-border);
            border-radius: 4px;
            color: var(--carlos-text);
            width: 100%;
            max-width: 280px;
            transition: border-color .15s, box-shadow .15s;
        }
        .pref-input:focus {
            border-color: var(--carlos-navy);
            outline: none;
            box-shadow: 0 0 0 2px rgba(72, 110, 189, .2);
            background: #fff;
        }
        select.pref-input { cursor: pointer; }

        /* Size variants */
        .input-xs { max-width: 70px !important; }
        .form-control-sm { max-width: 120px !important; }
        .input-md { max-width: 200px !important; }

        /* ─── Toggle switches ─── */
        .form-check-input {
            width: 2.4em;
            height: 1.2em;
            cursor: pointer;
            border: 1px solid #b0bec5;
        }
        .form-check-input:checked {
            background-color: var(--carlos-navy);
            border-color: var(--carlos-navy);
        }
        .form-check-input:focus {
            box-shadow: 0 0 0 2px rgba(72, 110, 189, .25);
        }

        /* ─── Colour picker ─── */
        input[type="color"] {
            width: 44px;
            height: 30px;
            padding: 2px;
            border: 1px solid var(--carlos-input-border);
            border-radius: 4px;
            cursor: pointer;
            background: var(--carlos-input-bg);
        }

        /* ─── Scrollable checkbox lists (encounter forms, eForms) ─── */
        .scroll-box {
            max-height: 140px;
            overflow-y: auto;
            border: 1px solid var(--carlos-border);
            border-radius: 4px;
            padding: 6px 8px;
            background: #fafbfc;
        }
        .scroll-box label {
            display: block;
            font-size: 12px;
            padding: 2px 4px;
            cursor: pointer;
            white-space: nowrap;
            border-radius: 3px;
        }
        .scroll-box label:hover { background: #e8eef7; }
        .scroll-box input[type="checkbox"] { margin-right: 6px; }

        /* ─── External link chips (Account & Advanced) ─── */
        .pref-links {
            display: flex;
            flex-wrap: wrap;
            gap: 6px;
        }
        .pref-link {
            display: inline-flex;
            align-items: center;
            gap: 6px;
            padding: 5px 12px;
            background: #e8eef7;
            border: 1px solid var(--carlos-border);
            border-radius: 4px;
            color: var(--carlos-navy);
            text-decoration: none;
            font-size: 12px;
            font-weight: 500;
            cursor: pointer;
            transition: background .15s, color .15s;
        }
        .pref-link:hover {
            background: var(--carlos-navy);
            color: #fff;
            border-color: var(--carlos-navy);
        }
        .pref-link i { font-size: 11px; }

        /* ─── Quick links table ─── */
        .ql-table { width: 100%; }
        .ql-table td {
            padding: 3px 6px;
            border: none;
            font-size: 12px;
            vertical-align: middle;
        }

        /* ─── Sticky footer bar ─── */
        .footer-bar {
            position: sticky;
            bottom: 0;
            background: #fff;
            border-top: 2px solid var(--carlos-navy);
            padding: 10px 20px;
            text-align: center;
            z-index: 100;
            box-shadow: 0 -2px 6px rgba(0,0,0,.08);
        }
        .btn-save {
            background: var(--carlos-navy);
            border: none;
            color: #fff;
            font-weight: 600;
            padding: 7px 28px;
            border-radius: 4px;
            font-size: 13px;
            letter-spacing: .3px;
        }
        .btn-save:hover { background: var(--carlos-navy-dark); color: #fff; }
        .btn-save i { margin-right: 6px; }
        .btn-close-pref {
            background: #e2e8f0;
            border: none;
            color: var(--carlos-text-secondary);
            font-weight: 500;
            padding: 7px 20px;
            border-radius: 4px;
            font-size: 13px;
        }
        .btn-close-pref:hover { background: #cbd5e1; color: var(--carlos-text); }
        .btn-close-pref i { margin-right: 6px; }

        /* ─── Utility classes ─── */
        .hint { font-size: 11px; color: #94a3b8; font-weight: 400; }
        .section-note {
            font-size: 11.5px;
            color: #64748b;
            margin-bottom: 8px;
            padding: 4px 0;
        }
        .section-note i { margin-right: 4px; }
        .badge-auto {
            display: inline-block;
            font-size: 10px;
            padding: 1px 6px;
            border-radius: 3px;
            background: var(--carlos-teal);
            color: #fff;
            font-weight: 500;
            margin-left: 6px;
            vertical-align: middle;
        }
    </style>
</head>
<body>
<form name="UPDATEPRE" method="post" action="providerupdatepreference.jsp" onsubmit="return checkTypeInAll()">
<input type="hidden" name="color_template" value="deepblue">
<input type="hidden" name="ticklerforproviderno" value="<%=Encode.forHtmlAttribute(props.getOrDefault(UserProperty.PROVIDER_FOR_TICKLER_WARNING, ""))%>">

<%-- ═══════════════════════════════════════════════════════════════════════
     HEADER BAR - Sticky navy header matching the schedule page top bar
     ═══════════════════════════════════════════════════════════════════════ --%>
<div class="pref-header">
    <h1><i class="fas fa-cog"></i> <fmt:message key="provider.providerpreference.pageTitle"/></h1>
    <span class="header-hint"><i class="fas fa-info-circle"></i> <fmt:message key="provider.providerpreference.headerHint"/></span>
</div>

<div class="pref-body">
<div class="accordion" id="prefAccordion">

<%-- ═══════════════════════════════════════════════════════════════════════
     SECTION 1: SCHEDULE & APPOINTMENTS
     Core scheduling parameters: hours, interval, groups, encounter forms
     ═══════════════════════════════════════════════════════════════════════ --%>
<div class="accordion-item">
    <h2 class="accordion-header">
        <button class="accordion-button" type="button"
                data-bs-toggle="collapse" data-bs-target="#secSchedule" aria-expanded="true" aria-controls="secSchedule">
            <i class="fas fa-calendar-alt section-icon"></i> <fmt:message key="provider.providerpreference.section.schedule"/>
        </button>
    </h2>
    <div id="secSchedule" class="accordion-collapse collapse show" data-bs-parent="#prefAccordion">
        <div class="accordion-body">

            <%-- Schedule time range --%>
            <div class="pref-row">
                <div class="pref-label"><fmt:message key="provider.providerpreference.label.startHour"/> <span class="hint"><fmt:message key="provider.providerpreference.hint.hours023"/></span></div>
                <div class="pref-value">
                    <input type="text" name="start_hour" value="<%=Encode.forHtmlAttribute(startHour)%>"
                           class="pref-input input-xs" maxlength="2">
                </div>
            </div>
            <div class="pref-row">
                <div class="pref-label"><fmt:message key="provider.providerpreference.label.endHour"/> <span class="hint"><fmt:message key="provider.providerpreference.hint.hours023"/></span></div>
                <div class="pref-value">
                    <input type="text" name="end_hour" value="<%=Encode.forHtmlAttribute(endHour)%>"
                           class="pref-input input-xs" maxlength="2">
                </div>
            </div>
            <div class="pref-row">
                <div class="pref-label"><fmt:message key="provider.providerpreference.label.period"/> <span class="hint"><fmt:message key="provider.providerpreference.hint.minPerSlot"/></span></div>
                <div class="pref-value">
                    <input type="text" name="every_min" value="<%=Encode.forHtmlAttribute(everyMin)%>"
                           class="pref-input input-xs" maxlength="2">
                </div>
            </div>

            <%-- Provider group assignment --%>
            <div class="pref-row">
                <div class="pref-label"><fmt:message key="provider.providerpreference.label.groupNo"/></div>
                <div class="pref-value" style="display:flex; align-items:center; gap:8px;">
                    <input type="text" name="mygroup_no" value="<%=Encode.forHtmlAttribute(myGroupNo != null ? myGroupNo : "")%>"
                           class="pref-input form-select-sm" maxlength="10">
                    <a href="providerdisplaymygroup.jsp" class="pref-link" target="_blank" rel="noopener noreferrer">
                        <i class="fas fa-users"></i> <fmt:message key="provider.providerpreference.link.viewGroups"/>
                    </a>
                </div>
            </div>

            <%-- Week view weekend toggle --%>
            <div class="pref-row">
                <div class="pref-label"><fmt:message key="provider.providerpreference.label.showWeekendsWeekView"/></div>
                <div class="pref-value">
                    <input type="checkbox" class="form-check-input" role="switch"
                           name="<%=UserProperty.SCHEDULE_WEEK_VIEW_WEEKENDS%>"
                           value="true" <%=weekendsEnabled ? "checked" : ""%>>
                </div>
            </div>

            <%-- Link name display length on appointment screen --%>
            <div class="pref-row">
                <div class="pref-label"><fmt:message key="provider.providerpreference.label.linkNameDisplayLength"/></div>
                <div class="pref-value">
                    <input type="text" name="appointmentScreenFormsNameDisplayLength"
                           value="<%=Encode.forHtmlAttribute(String.valueOf(providerPreference.getAppointmentScreenLinkNameDisplayLength()))%>"
                           class="pref-input input-xs">
                </div>
            </div>

            <%-- Encounter forms available on appointment screen --%>
            <div class="pref-row align-top">
                <div class="pref-label"><fmt:message key="provider.providerpreference.label.encounterFormsAppointments"/></div>
                <div class="pref-value">
                    <div class="scroll-box"><%
                        List<EncounterForm> encounterForms = ProviderPreferencesUIBean.getAllEncounterForms();
                        Collection<String> checkedEncounterFormNames =
                                ProviderPreferencesUIBean.getCheckedEncounterFormNames(providerNo);
                        for (EncounterForm ef : encounterForms) {
                            String chk = checkedEncounterFormNames.contains(ef.getFormName()) ? "checked" : "";
                    %><label><input type="checkbox" name="encounterFormName"
                                    value="<%=Encode.forHtmlAttribute(ef.getFormName())%>" <%=chk%>> <%=Encode.forHtml(ef.getFormName())%></label><%
                        }
                    %></div>
                </div>
            </div>

            <%-- eForms available on appointment screen.
                 Unlike encounter forms (which match by name), eForms match by numeric ID
                 against the provider's saved EformLink collection. --%>
            <div class="pref-row align-top">
                <div class="pref-label"><fmt:message key="provider.providerpreference.label.eFormsAppointments"/></div>
                <div class="pref-value">
                    <div class="scroll-box"><%
                        List<EForm> eforms = ProviderPreferencesUIBean.getAllEForms();
                        Collection<ProviderPreference.EformLink> checkedEFormIds =
                                ProviderPreferencesUIBean.getCheckedEFormIds(providerNo);
                        for (EForm eform : eforms) {
                            // Check if this eForm ID exists in the provider's saved links
                            String chk = "";
                            for (ProviderPreference.EformLink el : checkedEFormIds) {
                                if (eform.getId().equals(el.getAppointmentScreenEForm())) {
                                    chk = "checked";
                                    break;
                                }
                            }
                    %><label><input type="checkbox" name="eformId"
                                    value="<%=Encode.forHtmlAttribute(String.valueOf(eform.getId()))%>" <%=chk%>>
                        <%=Encode.forHtml(eform.getFormName())%></label><%
                        }
                    %></div>
                </div>
            </div>

            <%-- Quick links shown on the appointment screen --%>
            <div class="pref-row align-top">
                <div class="pref-label"><fmt:message key="provider.providerpreference.label.quickLinks"/></div>
                <fmt:message key="provider.providerpreference.quickLinks.remove" var="_qlRemoveLabel"/>
                <div class="pref-value">
                    <div class="scroll-box" style="max-height:100px; margin-bottom:6px"><%
                        Collection<ProviderPreference.QuickLink> quickLinks =
                                ProviderPreferencesUIBean.getQuickLinks(providerNo);
                        String qlRemoveLabel = (String) pageContext.getAttribute("_qlRemoveLabel");
                        for (ProviderPreference.QuickLink ql : quickLinks) {
                    %><div style="padding:2px 0">
                        <input type="button" value="<%=Encode.forHtmlAttribute(qlRemoveLabel)%>"
                               class="btn btn-sm btn-outline-danger"
                               style="font-size:10px; padding:1px 6px"
                               onclick="submitQuickLinkAction('remove','<%=Encode.forJavaScriptAttribute(ql.getName())%>','')">
                        <strong><%=Encode.forHtml(ql.getName())%></strong>:
                        <%=Encode.forHtml(ql.getUrl())%>
                    </div><%
                        }
                    %></div>
                    <table class="ql-table">
                        <tr>
                            <td style="width:50px"><fmt:message key="provider.providerpreference.quickLinks.name"/></td>
                            <td><input type="text" name="quickLinkName" class="pref-input" style="max-width:200px"></td>
                        </tr>
                        <tr>
                            <td><fmt:message key="provider.providerpreference.quickLinks.url"/></td>
                            <td>
                                <input type="text" name="quickLinkUrl" class="pref-input" style="max-width:200px">
                                <span class="hint">(tokens: &#36;{contextPath} &#36;{demographicId} &#36;{appointmentId} &#36;{providerId} &#36;{providerOhip} &#36;{demographicHin} &#36;{demographicVer})</span>
                            </td>
                        </tr>
                        <tr>
                            <td></td>
                            <td>
                                <button type="button" class="btn btn-sm mt-1"
                                        style="background:var(--carlos-navy); color:#fff; font-size:11px"
                                        onclick="addQuickLink()">
                                    <i class="fas fa-plus"></i> <fmt:message key="provider.providerpreference.quickLinks.addLink"/>
                                </button>
                            </td>
                        </tr>
                    </table>
                </div>
            </div>

        </div>
    </div>
</div>

<%-- ═══════════════════════════════════════════════════════════════════════
     SECTION 2: CONTACT INFORMATION
     Address and phone for prescriptions and consult letters
     ═══════════════════════════════════════════════════════════════════════ --%>
<div class="accordion-item">
    <h2 class="accordion-header">
        <button class="accordion-button collapsed" type="button"
                data-bs-toggle="collapse" data-bs-target="#secContact" aria-expanded="false" aria-controls="secContact">
            <i class="fas fa-address-card section-icon"></i> <fmt:message key="provider.providerpreference.section.contact"/>
        </button>
    </h2>
    <div id="secContact" class="accordion-collapse collapse" data-bs-parent="#prefAccordion">
        <div class="accordion-body">
            <div class="section-note">
                <i class="fas fa-info-circle"></i>
                <fmt:message key="provider.providerpreference.contact.sectionNote"/>
            </div>
            <div class="pref-row">
                <div class="pref-label"><fmt:message key="provider.providerpreference.label.address"/></div>
                <div class="pref-value">
                    <input type="text" name="rxAddress" class="pref-input"
                           value="<%=Encode.forHtmlAttribute(rxAddress)%>">
                </div>
            </div>
            <div class="pref-row">
                <div class="pref-label"><fmt:message key="provider.providerpreference.label.city"/></div>
                <div class="pref-value">
                    <input type="text" name="rxCity" class="pref-input"
                           value="<%=Encode.forHtmlAttribute(rxCity)%>">
                </div>
            </div>
            <div class="pref-row">
                <div class="pref-label"><fmt:message key="provider.providerpreference.label.province"/></div>
                <div class="pref-value">
                    <input type="text" name="rxProvince" class="pref-input form-select-sm"
                           value="<%=Encode.forHtmlAttribute(rxProvince)%>">
                </div>
            </div>
            <div class="pref-row">
                <div class="pref-label"><fmt:message key="provider.providerpreference.label.postalCode"/></div>
                <div class="pref-value">
                    <input type="text" name="rxPostal" class="pref-input form-select-sm"
                           value="<%=Encode.forHtmlAttribute(rxPostal)%>">
                </div>
            </div>
            <div class="pref-row">
                <div class="pref-label"><fmt:message key="provider.providerpreference.label.phoneNumber"/> <span class="hint"><fmt:message key="provider.providerpreference.hint.phoneFormat"/></span></div>
                <div class="pref-value">
                    <input type="text" name="rxPhone" class="pref-input input-md"
                           value="<%=Encode.forHtmlAttribute(rxPhone)%>">
                </div>
            </div>
            <div class="pref-row">
                <div class="pref-label"><fmt:message key="provider.providerpreference.label.faxNumber"/> <span class="hint"><fmt:message key="provider.providerpreference.hint.phoneFormat"/></span></div>
                <div class="pref-value">
                    <input type="text" name="faxnumber" class="pref-input input-md"
                           value="<%=Encode.forHtmlAttribute(faxNum)%>">
                </div>
            </div>
        </div>
    </div>
</div>

<%-- ═══════════════════════════════════════════════════════════════════════
     SECTION 3: PRESCRIPTIONS
     Rx page size, QR codes, interaction warnings, default quantities
     ═══════════════════════════════════════════════════════════════════════ --%>
<div class="accordion-item">
    <h2 class="accordion-header">
        <button class="accordion-button collapsed" type="button"
                data-bs-toggle="collapse" data-bs-target="#secRx" aria-expanded="false" aria-controls="secRx">
            <i class="fas fa-prescription-bottle-alt section-icon"></i> <fmt:message key="provider.providerpreference.section.prescriptions"/>
        </button>
    </h2>
    <div id="secRx" class="accordion-collapse collapse" data-bs-parent="#prefAccordion">
        <div class="accordion-body">
            <div class="pref-row">
                <div class="pref-label"><fmt:message key="provider.providerpreference.label.printQrCodesRx"/></div>
                <div class="pref-value">
                    <input type="checkbox" class="form-check-input" role="switch"
                           name="prescriptionQrCodes" <%=qrChecked ? "checked" : ""%>>
                </div>
            </div>
            <div class="pref-row">
                <div class="pref-label"><fmt:message key="provider.providerpreference.label.rxPageSize"/></div>
                <div class="pref-value">
                    <select name="rx_page_size" class="pref-input form-select-sm">
                        <option value=""><fmt:message key="provider.providerpreference.option.default"/></option>
                        <option value="PageSize.A4" <%="PageSize.A4".equals(rxPageSize)?"selected":""%>>A4</option>
                        <option value="PageSize.A6" <%="PageSize.A6".equals(rxPageSize)?"selected":""%>>A6</option>
                    </select>
                </div>
            </div>
            <div class="pref-row">
                <div class="pref-label"><fmt:message key="provider.providerpreference.label.useRx3"/></div>
                <div class="pref-value">
                    <input type="checkbox" class="form-check-input" role="switch"
                           name="rx_use_rx3" value="yes" <%=rxUseRx3 ? "checked" : ""%>>
                </div>
            </div>
            <div class="pref-row">
                <div class="pref-label"><fmt:message key="provider.providerpreference.label.showPatientDobRx"/></div>
                <div class="pref-value">
                    <input type="checkbox" class="form-check-input" role="switch"
                           name="rx_show_patient_dob" value="yes" <%=rxShowDOB ? "checked" : ""%>>
                </div>
            </div>
            <div class="pref-row">
                <div class="pref-label"><fmt:message key="provider.providerpreference.label.defaultRxQty"/></div>
                <div class="pref-value">
                    <input type="text" name="rx_default_quantity"
                           value="<%=Encode.forHtmlAttribute(rxDefaultQty)%>"
                           class="pref-input input-xs">
                </div>
            </div>
            <div class="pref-row">
                <div class="pref-label">
                    <fmt:message key="provider.providerpreference.label.rxInteractionWarningLevel"/>
                    <span class="badge-auto"><fmt:message key="provider.providerpreference.badge.autoSave"/></span>
                </div>
                <div class="pref-value">
                    <select id="rxInteractionWarningLevel" class="pref-input input-md">
                        <option value="0" <%="0".equals(warningLevel)?"selected":""%>><fmt:message key="provider.providerpreference.rxWarning.notSpecified"/></option>
                        <option value="1" <%="1".equals(warningLevel)?"selected":""%>><fmt:message key="provider.providerpreference.rxWarning.low"/></option>
                        <option value="2" <%="2".equals(warningLevel)?"selected":""%>><fmt:message key="provider.providerpreference.rxWarning.medium"/></option>
                        <option value="3" <%="3".equals(warningLevel)?"selected":""%>><fmt:message key="provider.providerpreference.rxWarning.high"/></option>
                        <option value="4" <%="4".equals(warningLevel)?"selected":""%>><fmt:message key="provider.providerpreference.rxWarning.rxNone"/></option>
                    </select>
                </div>
            </div>

        </div>
    </div>
</div>

<%-- ═══════════════════════════════════════════════════════════════════════
     SECTION 4: CLINICAL SETTINGS
     Default sex, HC type, billing Dx, CPP, stale note dates
     ═══════════════════════════════════════════════════════════════════════ --%>
<div class="accordion-item">
    <h2 class="accordion-header">
        <button class="accordion-button collapsed" type="button"
                data-bs-toggle="collapse" data-bs-target="#secClinical" aria-expanded="false" aria-controls="secClinical">
            <i class="fas fa-stethoscope section-icon"></i> <fmt:message key="provider.providerpreference.section.clinical"/>
        </button>
    </h2>
    <div id="secClinical" class="accordion-collapse collapse" data-bs-parent="#prefAccordion">
        <div class="accordion-body">
            <div class="pref-row">
                <div class="pref-label"><fmt:message key="provider.providerpreference.label.defaultSex"/></div>
                <div class="pref-value">
                    <select name="default_sex" class="pref-input input-xs">
                        <option value="">--</option>
                        <option value="M" <%="M".equals(defaultSex)?"selected":""%>>M</option>
                        <option value="F" <%="F".equals(defaultSex)?"selected":""%>>F</option>
                    </select>
                </div>
            </div>
            <div class="pref-row">
                <div class="pref-label"><fmt:message key="provider.providerpreference.label.defaultHcType"/></div>
                <div class="pref-value">
                    <select name="HC_Type" class="pref-input input-md">
                        <option value="">--</option>
                        <option value="AB" <%="AB".equals(hcType)?"selected":""%>><fmt:message key="provider.providerpreference.hcType.ab"/></option>
                        <option value="BC" <%="BC".equals(hcType)?"selected":""%>><fmt:message key="provider.providerpreference.hcType.bc"/></option>
                        <option value="MB" <%="MB".equals(hcType)?"selected":""%>><fmt:message key="provider.providerpreference.hcType.mb"/></option>
                        <option value="NB" <%="NB".equals(hcType)?"selected":""%>><fmt:message key="provider.providerpreference.hcType.nb"/></option>
                        <option value="NL" <%="NL".equals(hcType)?"selected":""%>><fmt:message key="provider.providerpreference.hcType.nl"/></option>
                        <option value="NT" <%="NT".equals(hcType)?"selected":""%>><fmt:message key="provider.providerpreference.hcType.nt"/></option>
                        <option value="NS" <%="NS".equals(hcType)?"selected":""%>><fmt:message key="provider.providerpreference.hcType.ns"/></option>
                        <option value="NU" <%="NU".equals(hcType)?"selected":""%>><fmt:message key="provider.providerpreference.hcType.nu"/></option>
                        <option value="ON" <%="ON".equals(hcType)?"selected":""%>><fmt:message key="provider.providerpreference.hcType.on"/></option>
                        <option value="PE" <%="PE".equals(hcType)?"selected":""%>><fmt:message key="provider.providerpreference.hcType.pe"/></option>
                        <option value="QC" <%="QC".equals(hcType)?"selected":""%>><fmt:message key="provider.providerpreference.hcType.qc"/></option>
                        <option value="SK" <%="SK".equals(hcType)?"selected":""%>><fmt:message key="provider.providerpreference.hcType.sk"/></option>
                        <option value="YT" <%="YT".equals(hcType)?"selected":""%>><fmt:message key="provider.providerpreference.hcType.yt"/></option>
                        <option value="US" <%="US".equals(hcType)?"selected":""%>><fmt:message key="provider.providerpreference.hcType.us"/></option>
                    </select>
                </div>
            </div>
            <div class="pref-row">
                <div class="pref-label"><fmt:message key="provider.providerpreference.label.defaultBillingDxCode"/></div>
                <div class="pref-value" style="display:flex; align-items:center; gap:8px;">
                    <input type="text" name="dxCode" id="dxCode"
                           value="<%=Encode.forHtmlAttribute(providerPreference.getDefaultDxCode() != null ? providerPreference.getDefaultDxCode() : "")%>"
                           class="pref-input input-xs" maxlength="5">
                    <button type="button" class="pref-link" data-bs-toggle="modal" data-bs-target="#dxSearchModal">
                        <i class="fas fa-search"></i> <fmt:message key="provider.providerpreference.btn.search"/>
                    </button>
                </div>
            </div>
            <div class="pref-row">
                <div class="pref-label"><fmt:message key="provider.providerpreference.label.cppSingleLine"/></div>
                <div class="pref-value">
                    <input type="checkbox" class="form-check-input" role="switch"
                           name="cpp_single_line" value="yes" <%=cppSingleLine ? "checked" : ""%>>
                </div>
            </div>

            <%-- Stale date controls for CME case notes.
                 "A" means show all notes; negative numbers (e.g., "-6") mean show only
                 notes from the last N months. Values 0-36 generate options "-0" to "-36"
                 ("-0" effectively shows no notes; "A" is typically the preferred default). --%>
            <div class="pref-row">
                <div class="pref-label"><fmt:message key="provider.providerpreference.label.staleDateCaseNotes"/></div>
                <fmt:message key="provider.providerpreference.staleDate.months" var="_staleDateMonthsWord"/>
                <div class="pref-value">
                    <select name="cme_note_date" class="pref-input input-md">
                        <option value="A" <%="A".equals(staleNoteDate)?"selected":""%>><fmt:message key="provider.providerpreference.staleDate.all"/></option><%
                        String staleDateMonthsWord = (String) pageContext.getAttribute("_staleDateMonthsWord");
                        for (int i = 0; i <= 36; i++) {
                    %><option value="-<%=i%>" <%=("-"+i).equals(staleNoteDate)?"selected":""%>><%=i%> <%=Encode.forHtml(staleDateMonthsWord)%></option><%
                        }
                    %></select>
                </div>
            </div>
            <div class="pref-row">
                <div class="pref-label"><fmt:message key="provider.providerpreference.label.staleDateFormat"/></div>
                <div class="pref-value">
                    <select name="cme_note_format" class="pref-input input-xs">
                        <option value="no" <%="no".equals(staleFormat)?"selected":""%>><fmt:message key="provider.providerpreference.staleDate.no"/></option>
                        <option value="yes" <%="yes".equals(staleFormat)?"selected":""%>><fmt:message key="provider.providerpreference.staleDate.yes"/></option>
                    </select>
                </div>
            </div>
        </div>
    </div>
</div>

<%-- ═══════════════════════════════════════════════════════════════════════
     SECTION 5: CONSULTATION
     Cutoff times, team warnings, paste format
     ═══════════════════════════════════════════════════════════════════════ --%>
<div class="accordion-item">
    <h2 class="accordion-header">
        <button class="accordion-button collapsed" type="button"
                data-bs-toggle="collapse" data-bs-target="#secConsult" aria-expanded="false" aria-controls="secConsult">
            <i class="fas fa-user-md section-icon"></i> <fmt:message key="provider.providerpreference.section.consultation"/>
        </button>
    </h2>
    <div id="secConsult" class="accordion-collapse collapse" data-bs-parent="#prefAccordion">
        <div class="accordion-body">
            <div class="pref-row">
                <div class="pref-label"><fmt:message key="provider.providerpreference.label.consultCutoff"/> <span class="hint"><fmt:message key="provider.providerpreference.hint.days"/></span></div>
                <div class="pref-value">
                    <input type="text" name="consultation_time_period_warning"
                           value="<%=Encode.forHtmlAttribute(consultCutoff)%>"
                           class="pref-input input-xs">
                </div>
            </div>
            <div class="pref-row">
                <div class="pref-label"><fmt:message key="provider.providerpreference.label.consultTeamWarning"/></div>
                <div class="pref-value">
                    <input type="text" name="consultation_team_warning"
                           value="<%=Encode.forHtmlAttribute(consultTeam)%>"
                           class="pref-input input-md">
                </div>
            </div>
            <div class="pref-row">
                <div class="pref-label"><fmt:message key="provider.providerpreference.label.workloadMgmt"/></div>
                <div class="pref-value">
                    <input type="text" name="workload_management"
                           value="<%=Encode.forHtmlAttribute(workloadMgmt)%>"
                           class="pref-input input-md">
                </div>
            </div>
            <div class="pref-row">
                <div class="pref-label"><fmt:message key="provider.providerpreference.label.consultPasteFmt"/></div>
                <div class="pref-value">
                    <select name="consultation_req_paste_fmt" class="pref-input input-md">
                        <option value=""><fmt:message key="provider.providerpreference.option.default"/></option>
                        <option value="single" <%="single".equals(consultPasteFmt)?"selected":""%>><fmt:message key="provider.providerpreference.pasteFmt.singleLine"/></option>
                        <option value="multi" <%="multi".equals(consultPasteFmt)?"selected":""%>><fmt:message key="provider.providerpreference.pasteFmt.multiLine"/></option>
                    </select>
                </div>
            </div>
        </div>
    </div>
</div>

<%-- ═══════════════════════════════════════════════════════════════════════
     SECTION 6: DISPLAY & UI
     Provider colour, encounter window, document display, eDoc settings
     ═══════════════════════════════════════════════════════════════════════ --%>
<div class="accordion-item">
    <h2 class="accordion-header">
        <button class="accordion-button collapsed" type="button"
                data-bs-toggle="collapse" data-bs-target="#secDisplay" aria-expanded="false" aria-controls="secDisplay">
            <i class="fas fa-desktop section-icon"></i> <fmt:message key="provider.providerpreference.section.display"/>
        </button>
    </h2>
    <div id="secDisplay" class="accordion-collapse collapse" data-bs-parent="#prefAccordion">
        <div class="accordion-body">

            <%-- Encounter window sizing --%>
            <div class="pref-row">
                <div class="pref-label"><fmt:message key="provider.providerpreference.label.encWinWidth"/> <span class="hint"><fmt:message key="provider.providerpreference.hint.px"/></span></div>
                <div class="pref-value">
                    <input type="text" name="encounterWindowWidth"
                           value="<%=Encode.forHtmlAttribute(encWinWidth)%>"
                           class="pref-input input-xs" placeholder="px">
                </div>
            </div>
            <div class="pref-row">
                <div class="pref-label"><fmt:message key="provider.providerpreference.label.encWinHeight"/> <span class="hint"><fmt:message key="provider.providerpreference.hint.px"/></span></div>
                <div class="pref-value">
                    <input type="text" name="encounterWindowHeight"
                           value="<%=Encode.forHtmlAttribute(encWinHeight)%>"
                           class="pref-input input-xs" placeholder="px">
                </div>
            </div>
            <div class="pref-row">
                <div class="pref-label"><fmt:message key="provider.providerpreference.label.maxEncWin"/></div>
                <div class="pref-value">
                    <input type="checkbox" class="form-check-input" role="switch"
                           name="encounterWindowMaximize" value="yes" <%=encWinMax ? "checked" : ""%>>
                </div>
            </div>
            <div class="pref-row">
                <div class="pref-label"><fmt:message key="provider.providerpreference.label.openInTabs"/></div>
                <div class="pref-value">
                    <input type="checkbox" class="form-check-input" role="switch"
                           name="encounter_open_in_tab" value="yes" <%=encOpenInTab ? "checked" : ""%>>
                </div>
            </div>
            <div class="pref-row">
                <div class="pref-label"><fmt:message key="provider.providerpreference.label.quickChartSize"/> <span class="hint"><fmt:message key="provider.providerpreference.hint.px"/></span></div>
                <div class="pref-value">
                    <input type="text" name="quickChartSize"
                           value="<%=Encode.forHtmlAttribute(quickChartSize)%>"
                           class="pref-input input-xs" placeholder="px">
                </div>
            </div>
            <div class="pref-row">
                <div class="pref-label"><fmt:message key="provider.providerpreference.label.maxPatientNameLen"/></div>
                <div class="pref-value">
                    <input type="text" name="patient_name_length"
                           value="<%=Encode.forHtmlAttribute(patientNameLen)%>"
                           class="pref-input input-xs">
                </div>
            </div>
            <div class="pref-row">
                <div class="pref-label"><fmt:message key="provider.providerpreference.label.displayDocAs"/></div>
                <div class="pref-value">
                    <select name="display_document_as" class="pref-input form-select-sm">
                        <option value=""><fmt:message key="provider.providerpreference.option.default"/></option>
                        <option value="PDF" <%="PDF".equals(displayDocAs)?"selected":""%>>PDF</option>
                        <option value="Image" <%="Image".equals(displayDocAs)?"selected":""%>><fmt:message key="provider.providerpreference.displayDoc.image"/></option>
                    </select>
                </div>
            </div>
            <div class="pref-row">
                <div class="pref-label"><fmt:message key="provider.providerpreference.label.eDocInDocReport"/></div>
                <div class="pref-value">
                    <input type="checkbox" class="form-check-input" role="switch"
                           name="edoc_browser_in_document_report" value="yes" <%=eDocInReport ? "checked" : ""%>>
                </div>
            </div>
            <div class="pref-row">
                <div class="pref-label"><fmt:message key="provider.providerpreference.label.eDocInMasterRecord"/></div>
                <div class="pref-value">
                    <input type="checkbox" class="form-check-input" role="switch"
                           name="edoc_browser_in_master_file" value="yes" <%=eDocInMaster ? "checked" : ""%>>
                </div>
            </div>
        </div>
    </div>
</div>

<%-- ═══════════════════════════════════════════════════════════════════════
     SECTION 7: LAB, PREVENTION & MESSAGING
     Lab acknowledgement, prevention warnings, dashboard
     ═══════════════════════════════════════════════════════════════════════ --%>
<div class="accordion-item">
    <h2 class="accordion-header">
        <button class="accordion-button collapsed" type="button"
                data-bs-toggle="collapse" data-bs-target="#secLab" aria-expanded="false" aria-controls="secLab">
            <i class="fas fa-flask section-icon"></i> <fmt:message key="provider.providerpreference.section.lab"/>
        </button>
    </h2>
    <div id="secLab" class="accordion-collapse collapse" data-bs-parent="#prefAccordion">
        <div class="accordion-body">

            <%-- Lab acknowledgement --%>
            <div class="pref-row">
                <div class="pref-label"><fmt:message key="provider.providerpreference.label.disableLabAckComment"/></div>
                <div class="pref-value">
                    <input type="checkbox" class="form-check-input" role="switch"
                           name="lab_ack_comment" value="yes" <%=labAckComment ? "checked" : ""%>>
                </div>
            </div>

            <%-- Dashboard sharing --%>
            <div class="pref-row">
                <div class="pref-label"><i class="fas fa-share-alt" style="margin-right:4px"></i> <fmt:message key="provider.providerpreference.label.shareDashboard"/></div>
                <div class="pref-value">
                    <input type="checkbox" class="form-check-input" role="switch"
                           name="dashboard_share" value="yes" <%=dashboardShare ? "checked" : ""%>>
                </div>
            </div>

            <%-- Prevention warning toggles (stored as "true"/"false") --%>
            <div class="pref-row">
                <div class="pref-label"><fmt:message key="provider.providerpreference.label.prevSsoWarning"/></div>
                <div class="pref-value">
                    <input type="checkbox" class="form-check-input" role="switch"
                           name="prevention_sso_warning" value="true" <%=prevSSO ? "checked" : ""%>>
                </div>
            </div>
            <div class="pref-row">
                <div class="pref-label"><fmt:message key="provider.providerpreference.label.prevIspaWarning"/></div>
                <div class="pref-value">
                    <input type="checkbox" class="form-check-input" role="switch"
                           name="prevention_ispa_warning" value="true" <%=prevISPA ? "checked" : ""%>>
                </div>
            </div>
            <div class="pref-row">
                <div class="pref-label"><fmt:message key="provider.providerpreference.label.prevNonIspaWarning"/></div>
                <div class="pref-value">
                    <input type="checkbox" class="form-check-input" role="switch"
                           name="prevention_non_ispa_warning" value="true" <%=prevNonISPA ? "checked" : ""%>>
                </div>
            </div>

            <%-- Favourite eForm group dropdown --%>
            <div class="pref-row">
                <div class="pref-label"><fmt:message key="provider.providerpreference.label.favEformGroup"/></div>
                <div class="pref-value">
                    <select name="favourite_eform_group" class="pref-input input-md">
                        <option value=""><fmt:message key="provider.providerpreference.option.none"/></option><%
                        for (HashMap<String, String> grp : eformGroups) {
                            String gName = grp.get("groupName") != null ? grp.get("groupName") : "";
                            String sel = gName.equals(eformGroup) ? "selected" : "";
                    %><option value="<%=Encode.forHtmlAttribute(gName)%>" <%=sel%>><%=Encode.forHtml(gName)%></option><%
                        }
                    %></select>
                </div>
            </div>

            <%-- External links for complex lab settings UIs --%>
            <div class="pref-row">
                <div class="pref-label"><fmt:message key="provider.providerpreference.label.labRecallMacros"/></div>
                <div class="pref-value pref-links">
                    <a href="<%=request.getContextPath()%>/setProviderStaleDate.do?method=viewLabRecall"
                       class="pref-link" target="_blank" rel="noopener noreferrer">
                        <i class="fas fa-redo"></i> <fmt:message key="provider.providerpreference.link.labRecallSettings"/>
                    </a>
                    <a href="<%=request.getContextPath()%>/setProviderStaleDate.do?method=viewLabMacroPrefs"
                       class="pref-link" target="_blank" rel="noopener noreferrer">
                        <i class="fas fa-code"></i> <fmt:message key="provider.providerpreference.link.labMacros"/>
                    </a>
                </div>
            </div>
        </div>
    </div>
</div>

<%-- ═══════════════════════════════════════════════════════════════════════
     SECTION 8: APPOINTMENT CARD
     Clinic name, phone, fax printed on appointment reminder cards
     ═══════════════════════════════════════════════════════════════════════ --%>
<div class="accordion-item">
    <h2 class="accordion-header">
        <button class="accordion-button collapsed" type="button"
                data-bs-toggle="collapse" data-bs-target="#secApptCard" aria-expanded="false" aria-controls="secApptCard">
            <i class="fas fa-id-card section-icon"></i> <fmt:message key="provider.providerpreference.section.apptCard"/>
        </button>
    </h2>
    <div id="secApptCard" class="accordion-collapse collapse" data-bs-parent="#prefAccordion">
        <div class="accordion-body">
            <div class="section-note">
                <i class="fas fa-info-circle"></i>
                <fmt:message key="provider.providerpreference.apptCard.sectionNote"/>
            </div>
            <div class="pref-row">
                <div class="pref-label"><fmt:message key="provider.providerpreference.label.clinicName"/></div>
                <div class="pref-value">
                    <input type="text" name="appointmentCardName" class="pref-input"
                           value="<%=Encode.forHtmlAttribute(apptCardName)%>">
                </div>
            </div>
            <div class="pref-row">
                <div class="pref-label"><fmt:message key="provider.providerpreference.label.phoneNumber"/></div>
                <div class="pref-value">
                    <input type="text" name="appointmentCardPhone" class="pref-input input-md"
                           value="<%=Encode.forHtmlAttribute(apptCardPhone)%>">
                </div>
            </div>
            <div class="pref-row">
                <div class="pref-label"><fmt:message key="provider.providerpreference.label.faxNumber"/></div>
                <div class="pref-value">
                    <input type="text" name="appointmentCardFax" class="pref-input input-md"
                           value="<%=Encode.forHtmlAttribute(apptCardFax)%>">
                </div>
            </div>
        </div>
    </div>
</div>

<%-- ═══════════════════════════════════════════════════════════════════════
     SECTION 9: BILLING (conditional)
     Only rendered when:
       1) TORONTO_RFQ property is "no" or unset (Toronto RFQ hides billing UI)
       2) The logged-in user has read ("r") access to the "_billing" security object
     The billing form dropdown is populated from ctl_billingservice table.
     BC-specific billing preferences link is shown if billregion=BC.
     ═══════════════════════════════════════════════════════════════════════ --%>
<oscar:oscarPropertiesCheck property="TORONTO_RFQ" value="no" defaultVal="true">
<security:oscarSec roleName="<%=roleName$%>" objectName="_billing" rights="r">
<div class="accordion-item">
    <h2 class="accordion-header">
        <button class="accordion-button collapsed" type="button"
                data-bs-toggle="collapse" data-bs-target="#secBilling" aria-expanded="false" aria-controls="secBilling">
            <i class="fas fa-file-invoice-dollar section-icon"></i> <fmt:message key="provider.providerpreference.section.billing"/>
        </button>
    </h2>
    <div id="secBilling" class="accordion-collapse collapse" data-bs-parent="#prefAccordion">
        <div class="accordion-body">
            <div class="pref-row">
                <div class="pref-label"><fmt:message key="provider.providerpreference.label.defaultBillingForm"/></div>
                <div class="pref-value">
                    <select name="default_servicetype" class="pref-input input-md">
                        <option value="no"><fmt:message key="provider.providerpreference.billing.noneOption"/></option><%
                        String def = providerPreference.getDefaultServiceType();
                        for (Object[] result : ctlBillingServiceDao.getUniqueServiceTypes("A")) {
                    %><option value="<%=Encode.forHtmlAttribute((String)result[0])%>"
                              <%=((String)result[0]).equals(def)?"selected":""%>><%=Encode.forHtml((String)result[1])%></option><%
                        }
                    %></select>
                </div>
            </div>
            <%-- BC-specific billing preferences link --%>
            <div class="pref-row">
                <div class="pref-label"><fmt:message key="provider.providerpreference.label.regionalBillingSettings"/></div>
                <div class="pref-value pref-links"><%
                    String br = CarlosProperties.getInstance().getProperty("billregion");
                    if ("BC".equals(br)) {
                %><a href="<%=request.getContextPath()%>/billing/CA/BC/viewBillingPreferencesAction.do?providerNo=<%=Encode.forUriComponent(providerNo)%>"
                     class="pref-link" target="_blank" rel="noopener noreferrer">
                    <i class="fas fa-external-link-alt"></i> <fmt:message key="provider.providerpreference.link.bcBillingPrefs"/>
                </a><%
                    }
                %></div>
            </div>
        </div>
    </div>
</div>
</security:oscarSec>
</oscar:oscarPropertiesCheck>

<%-- ═══════════════════════════════════════════════════════════════════════
     SECTION 10: SIGNATURE STAMP
     Upload or draw a signature image used in consults, prescriptions, and eForms.
     ═══════════════════════════════════════════════════════════════════════ --%>
<div class="accordion-item">
    <h2 class="accordion-header">
        <button class="accordion-button collapsed" type="button"
                data-bs-toggle="collapse" data-bs-target="#secSignatureStamp" aria-expanded="false" aria-controls="secSignatureStamp">
            <i class="fas fa-signature section-icon"></i> <fmt:message key="provider.providerpreference.signatureStamp.title"/>
        </button>
    </h2>
    <div id="secSignatureStamp" class="accordion-collapse collapse" data-bs-parent="#prefAccordion">
        <div class="accordion-body">
            <div class="section-note">
                <i class="fas fa-info-circle"></i>
                <fmt:message key="provider.providerpreference.signatureStamp.infoNote"/>
            </div>

            <%-- Current signature preview --%>
            <fmt:message key="provider.providerpreference.signatureStamp.altCurrentSig" var="altCurrentSig"/>
            <div class="mb-3">
                <label class="pref-label"><fmt:message key="provider.providerpreference.signatureStamp.labelCurrentSig"/></label>
                <div id="sigPreviewArea" style="border:1px solid var(--carlos-border); border-radius:4px; padding:10px; background:#fff; min-height:80px; display:flex; align-items:center; justify-content:center;">
                    <% if (hasConsultSignature) { %>
                        <img id="sigPreviewImg" src="<%=request.getContextPath()%>/provider/providerSignatureImage.do"
                             alt="<%=Encode.forHtmlAttribute((String)pageContext.getAttribute("altCurrentSig"))%>" style="max-width:100%; max-height:120px;"/>
                    <% } else { %>
                        <span id="sigPlaceholder" style="color:#999; font-style:italic;"><fmt:message key="provider.providerpreference.signatureStamp.noSigUploaded"/></span>
                        <img id="sigPreviewImg" src="" alt="<%=Encode.forHtmlAttribute((String)pageContext.getAttribute("altCurrentSig"))%>" style="max-width:100%; max-height:120px; display:none;"/>
                    <% } %>
                </div>
            </div>

            <div id="sigStatusMsg" class="alert" style="display:none;" role="alert"></div>

            <%-- Upload signature file --%>
            <div class="mb-3">
                <label class="pref-label" for="sigFileInput"><fmt:message key="provider.providerpreference.signatureStamp.labelUpload"/></label>
                <div class="d-flex align-items-center gap-2">
                    <input type="file" id="sigFileInput" accept="image/png,image/jpeg,image/gif"
                           class="form-control form-control-sm" style="max-width:300px;"/>
                    <button type="button" class="btn btn-sm btn-primary" onclick="uploadSignatureFile()">
                        <i class="fas fa-upload"></i> <fmt:message key="provider.providerpreference.signatureStamp.btnUpload"/>
                    </button>
                </div>
                <small class="text-muted"><fmt:message key="provider.providerpreference.signatureStamp.uploadHint"/></small>
            </div>

            <%-- Draw signature --%>
            <div class="mb-3">
                <label class="pref-label"><fmt:message key="provider.providerpreference.signatureStamp.labelDraw"/></label>
                <div style="border:1px solid var(--carlos-border); border-radius:4px; background:#fff; padding:4px; display:inline-block;">
                    <canvas id="sigCanvas" width="500" height="150"
                            style="cursor:crosshair; display:block; touch-action:none;"></canvas>
                </div>
                <div class="mt-2 d-flex gap-2">
                    <button type="button" class="btn btn-sm btn-primary" onclick="saveDrawnSignature()">
                        <i class="fas fa-save"></i> <fmt:message key="provider.providerpreference.signatureStamp.btnSaveDrawing"/>
                    </button>
                    <button type="button" class="btn btn-sm btn-outline-secondary" onclick="clearCanvas()">
                        <i class="fas fa-eraser"></i> <fmt:message key="provider.providerpreference.signatureStamp.btnClear"/>
                    </button>
                </div>
            </div>

            <%-- Delete signature --%>
            <% if (hasConsultSignature) { %>
            <div class="mb-0" id="sigDeleteSection">
                <button type="button" class="btn btn-sm btn-outline-danger" onclick="deleteSignature()">
                    <i class="fas fa-trash-alt"></i> <fmt:message key="provider.providerpreference.signatureStamp.btnDelete"/>
                </button>
            </div>
            <% } %>
        </div>
    </div>
</div>

<%-- ═══════════════════════════════════════════════════════════════════════
     SECTION 11: ACCOUNT & ADVANCED
     External links for password, printer, API clients, etc.
     ═══════════════════════════════════════════════════════════════════════ --%>
<div class="accordion-item">
    <h2 class="accordion-header">
        <button class="accordion-button collapsed" type="button"
                data-bs-toggle="collapse" data-bs-target="#secAccount" aria-expanded="false" aria-controls="secAccount">
            <i class="fas fa-user-cog section-icon"></i> <fmt:message key="provider.providerpreference.section.account"/>
        </button>
    </h2>
    <div id="secAccount" class="accordion-collapse collapse" data-bs-parent="#prefAccordion">
        <div class="accordion-body">
            <div class="section-note">
                <i class="fas fa-external-link-alt"></i>
                <fmt:message key="provider.providerpreference.account.sectionNote"/>
            </div>
            <div class="pref-links">
                <a href="providerchangepassword.jsp" class="pref-link" target="_blank" rel="noopener noreferrer">
                    <i class="fas fa-key"></i> <fmt:message key="provider.providerpreference.link.changePassword"/>
                </a>
                <a href="providerSignature.jsp" class="pref-link" target="_blank" rel="noopener noreferrer">
                    <i class="fas fa-pen-nib"></i> <fmt:message key="provider.providerpreference.linkEditTextSig"/>
                </a>
                <a href="providerPrinter.jsp" class="pref-link" target="_blank" rel="noopener noreferrer">
                    <i class="fas fa-print"></i> <fmt:message key="provider.providerpreference.link.setDefaultPrinter"/>
                </a>
                <a href="<%=request.getContextPath()%>/provider/CppPreferences.do" class="pref-link" target="_blank" rel="noopener noreferrer">
                    <i class="fas fa-columns"></i> <fmt:message key="provider.providerpreference.link.configureEChartCpp"/>
                </a>
                <a href="clients.jsp" class="pref-link" target="_blank" rel="noopener noreferrer">
                    <i class="fas fa-plug"></i> <fmt:message key="provider.providerpreference.link.manageApiClients"/>
                </a>
                <a href="<%= request.getContextPath() %>/admin/displayDocumentDescriptionTemplate.jsp"
                   class="pref-link" target="_blank" rel="noopener noreferrer">
                    <i class="fas fa-file-alt"></i> <fmt:message key="provider.providerpreference.link.docDescTemplate"/>
                </a>
                <a href="<%=request.getContextPath()%>/setProviderStaleDate.do?method=viewTicklerTaskAssignee"
                   class="pref-link" target="_blank" rel="noopener noreferrer">
                    <i class="fas fa-tasks"></i> <fmt:message key="provider.providerpreference.link.ticklerPrefs"/>
                </a>
            </div>
        </div>
    </div>
</div>

</div><%-- end accordion --%>
</div><%-- end pref-body --%>

<%-- ═══════════════════════════════════════════════════════════════════════
     STICKY FOOTER BAR - Save/Close buttons always visible at bottom
     ═══════════════════════════════════════════════════════════════════════ --%>
<div class="footer-bar">
    <button type="submit" class="btn btn-save">
        <i class="fas fa-save"></i> <fmt:message key="provider.providerpreference.btn.saveAll"/>
    </button>
    <button type="button" class="btn btn-close-pref ms-2" onclick="closePreferences()">
        <i class="fas fa-times"></i> <fmt:message key="provider.providerpreference.btn.close"/>
    </button>
</div>

</form>

<%-- ═══════════════════════════════════════════════════════════════════════
     DX CODE SEARCH MODAL - Inline search for billing diagnostic codes
     Loads billingDigSearch.jsp in an iframe, overrides its CodeAttach()
     to write back to the dxCode input and close the modal.
     ═══════════════════════════════════════════════════════════════════════ --%>
<div class="modal fade" id="dxSearchModal" tabindex="-1" aria-labelledby="dxSearchLabel" aria-hidden="true">
    <div class="modal-dialog modal-lg modal-dialog-centered">
        <div class="modal-content">
            <div class="modal-header" style="background:var(--carlos-navy);color:#fff;padding:8px 16px">
                <h5 class="modal-title" id="dxSearchLabel" style="font-size:14px;margin:0">
                    <i class="fas fa-search" style="margin-right:6px"></i> <fmt:message key="provider.providerpreference.modal.dxSearch.title"/>
                </h5>
                <button type="button" class="btn-close btn-close-white" data-bs-dismiss="modal" aria-label="Close"></button>
            </div>
            <div class="modal-body" style="padding:0;height:450px">
                <fmt:message key="provider.providerpreference.modal.dxSearch.iframeTitle" var="_dxSearchIframeTitle"/>
                <iframe id="dxSearchFrame" style="width:100%;height:100%;border:none" title="<%=Encode.forHtmlAttribute((String)pageContext.getAttribute("_dxSearchIframeTitle"))%>"></iframe>
            </div>
        </div>
    </div>
</div>


<%-- JS message variables for schedule validation and quick link alerts --%>
<fmt:message key="provider.providerpreference.js.scheduleInvalidNumbers" var="_jsSchedInvalidNumbers"/>
<fmt:message key="provider.providerpreference.js.endHourRange" var="_jsEndHourRange"/>
<fmt:message key="provider.providerpreference.js.startBeforeEnd" var="_jsStartBeforeEnd"/>
<fmt:message key="provider.providerpreference.js.periodInvalid" var="_jsPeriodInvalid"/>
<fmt:message key="provider.providerpreference.js.periodMax120" var="_jsPeriodMax120"/>
<fmt:message key="provider.providerpreference.js.quickLinkRequired" var="_jsQuickLinkRequired"/>
<fmt:message key="provider.providerpreference.js.quickLinkNavigate" var="_jsQuickLinkNavigate"/>
<fmt:message key="provider.providerpreference.js.rxWarnSaveFailed.session" var="_jsRxWarnSession"/>
<fmt:message key="provider.providerpreference.js.rxWarnSaveFailed.retry" var="_jsRxWarnRetry"/>
<script>
var _i18n = {
    schedInvalidNumbers: '<%=Encode.forJavaScript((String)pageContext.getAttribute("_jsSchedInvalidNumbers"))%>',
    endHourRange:        '<%=Encode.forJavaScript((String)pageContext.getAttribute("_jsEndHourRange"))%>',
    startBeforeEnd:      '<%=Encode.forJavaScript((String)pageContext.getAttribute("_jsStartBeforeEnd"))%>',
    periodInvalid:       '<%=Encode.forJavaScript((String)pageContext.getAttribute("_jsPeriodInvalid"))%>',
    periodMax120:        '<%=Encode.forJavaScript((String)pageContext.getAttribute("_jsPeriodMax120"))%>',
    quickLinkRequired:   '<%=Encode.forJavaScript((String)pageContext.getAttribute("_jsQuickLinkRequired"))%>',
    quickLinkNavigate:   '<%=Encode.forJavaScript((String)pageContext.getAttribute("_jsQuickLinkNavigate"))%>',
    rxWarnSession:       '<%=Encode.forJavaScript((String)pageContext.getAttribute("_jsRxWarnSession"))%>',
    rxWarnRetry:         '<%=Encode.forJavaScript((String)pageContext.getAttribute("_jsRxWarnRetry"))%>'
};

/**
 * Sets initial focus on the group number field for quick editing.
 */
function setfocus() {
    document.UPDATEPRE.mygroup_no.focus();
    document.UPDATEPRE.mygroup_no.select();
}

// Ensure initial focus is applied once the DOM is ready
document.addEventListener('DOMContentLoaded', function () {
    if (document.UPDATEPRE && document.UPDATEPRE.mygroup_no) {
        setfocus();
    }
});
/**
 * Validates schedule parameters before form submission.
 * Ensures start < end, values are numeric, and period fits within the range.
 * Also enforces server-side 120-minute maximum to prevent confusing UX.
 * @returns {boolean} true if validation passes, false to prevent submission
 */
function checkTypeInAll() {
    var s = parseInt(document.UPDATEPRE.start_hour.value);
    var e = parseInt(document.UPDATEPRE.end_hour.value);
    var i = parseInt(document.UPDATEPRE.every_min.value);

    // All three schedule fields must be valid numbers
    if (isNaN(s) || isNaN(e) || isNaN(i)) {
        alert(_i18n.schedInvalidNumbers);
        return false;
    }
    // End hour must be within 24-hour range
    if (e >= 24) {
        alert(_i18n.endHourRange);
        return false;
    }
    // Start must come before end
    if (s >= e) {
        alert(_i18n.startBeforeEnd);
        return false;
    }
    // Period must be positive and fit within the hour range
    if (i <= 0 || i > (e - s) * 60) {
        alert(_i18n.periodInvalid);
        return false;
    }
    // Enforce server-side 120-minute maximum to match backend validation
    if (i > 120) {
        alert(_i18n.periodMax120);
        // Allow submission - server will clamp the value and notify user
        return true;
    }
    return true;
}

/**
 * Submits a quick link action (add/remove) via POST form.
 * @param {string} action - The action to perform ('add' or 'remove')
 * @param {string} name - The quick link name
 * @param {string} url - The quick link URL; omitted from form when falsy (e.g., for 'remove')
 */
function submitQuickLinkAction(action, name, url) {
    var form = document.createElement('form');
    form.method = 'post';
    form.action = 'providerPreferenceQuickLinksAction.jsp';
    var fields = {action: action, name: name};
    if (url) { fields.url = url; }
    for (var key in fields) {
        var input = document.createElement('input');
        input.type = 'hidden';
        input.name = key;
        input.value = fields[key];
        form.appendChild(input);
    }
    document.body.appendChild(form);
    form.submit();
}

/**
 * Adds a quick link by submitting to the quick links action JSP via POST.
 * Quick links appear on the appointment screen for fast access to URLs.
 */
function addQuickLink() {
    var name = document.UPDATEPRE.quickLinkName.value.trim();
    var url = document.UPDATEPRE.quickLinkUrl.value.trim();
    if (!name || !url) {
        alert(_i18n.quickLinkRequired);
        return;
    }
    if (!confirm(_i18n.quickLinkNavigate)) {
        return;
    }
    submitQuickLinkAction('add', name, url);
}

/**
 * Closes the preferences window/tab. Tries window.close() first (works when
 * opened as a popup), falls back to browser history, then to the main provider page.
 */
function closePreferences() {
    try { window.close(); } catch(e) { console.warn('Could not close window:', e.message); }
    setTimeout(function() {
        if (!window.closed) {
            if (history.length > 1) {
                history.back();
            } else {
                location.href = '<%= request.getContextPath() %>/provider/providercontrol.jsp';
            }
        }
    }, 150);
}

// ── Auto-save listeners ───────────────────────────────────────────────
// These two fields save immediately on change without requiring the main
// form submission, using fetch() with CSRF token.

function flashAutoSave(el, success) {
    el.style.borderColor = success ? '#00A488' : '#dc3545';
    if (success) {
        setTimeout(function() { el.style.borderColor = ''; }, 1500);
    }
    // On failure the red border stays until the user interacts with the field again
}

/**
 * Checks whether a fetch response looks like a valid save response (not a
 * session-expired login redirect page). Returns false if the response body
 * contains HTML that looks like a full page (e.g., a login redirect).
 */
function isValidAutoSaveResponse(status, body) {
    if (status !== 200) return false;
    var text = (body || '').trim();
    if (text.indexOf('<html') !== -1 || text.indexOf('login') !== -1) return false;
    return true;
}

// Rx Interaction Warning Level - saves via dedicated endpoint
(function() {
    var el = document.getElementById('rxInteractionWarningLevel');
    var previousValue = el.value;
    el.addEventListener('focus', function() { previousValue = this.value; });
    el.addEventListener('change', function() {
        var self = this;
        var csrfEl = document.querySelector('input[name="CSRF-TOKEN"]');
        var csrfToken = csrfEl ? csrfEl.value : '';
        fetch('<c:out value="${ctx}"/>/provider/rxInteractionWarningLevel.do', {
            method: 'POST',
            credentials: 'same-origin',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
                'X-Requested-With': 'XMLHttpRequest',
                'CSRF-TOKEN': csrfToken
            },
            body: 'method=update&value=' + encodeURIComponent(self.value)
        }).then(function(r) {
            return r.text().then(function(body) {
                if (isValidAutoSaveResponse(r.status, body)) {
                    flashAutoSave(self, true);
                    previousValue = self.value;
                } else {
                    console.error('Rx interaction warning level: unexpected response (possible session expiry)');
                    self.value = previousValue;
                    flashAutoSave(self, false);
                    alert(_i18n.rxWarnSession);
                }
            });
        }).catch(function(err) {
            console.error('Failed to save rx interaction warning level:', err);
            self.value = previousValue;
            flashAutoSave(self, false);
            alert(_i18n.rxWarnRetry);
        });
    });
})();

// ── Dx Code Search Modal ─────────────────────────────────────────────
// Loads billingDigSearch.jsp in an iframe when the modal opens.
// Overrides the iframe's CodeAttach() so selecting a code writes back
// to the dxCode input and closes the modal (no popup needed).

document.getElementById('dxSearchModal').addEventListener('show.bs.modal', function() {
    var code = document.getElementById('dxCode').value;
    var frame = document.getElementById('dxSearchFrame');
    frame.src = '<%= request.getContextPath() %>/billing/CA/ON/billingDigSearch.jsp?name='
        + encodeURIComponent(code) + '&search=';
    frame.onload = function() {
        try {
            frame.contentWindow.CodeAttach = function(file) {
                if (typeof file === 'string' && file.length >= 3) {
                    document.getElementById('dxCode').value = file.substring(0, 3);
                } else if (typeof file === 'string' && file.length > 0) {
                    document.getElementById('dxCode').value = file;
                }
                var modal = bootstrap.Modal.getInstance(document.getElementById('dxSearchModal'));
                if (modal) { modal.hide(); }
            };
        } catch(e) {
            if (e.name === 'SecurityError') {
                console.warn('Dx code search: cross-origin iframe, code selection may not work automatically.');
            } else {
                console.error('Dx code search: failed to attach code handler:', e);
            }
        }
    };
});

document.getElementById('dxSearchModal').addEventListener('hidden.bs.modal', function() {
    document.getElementById('dxSearchFrame').src = 'about:blank';
});
</script>

<%-- ═══════════════════════════════════════════════════════════════════════
     SIGNATURE STAMP - Canvas drawing and upload/delete AJAX handlers.
     Operates independently from the main preferences form.
     ═══════════════════════════════════════════════════════════════════════ --%>
<%-- JS message variables for signature stamp AJAX handlers --%>
<fmt:message key="provider.providerpreference.signatureStamp.msgSelectFirst" var="_sigSelectFirst"/>
<fmt:message key="provider.providerpreference.signatureStamp.msgUploadSuccess" var="_sigUploadSuccess"/>
<fmt:message key="provider.providerpreference.signatureStamp.msgUploadFailed" var="_sigUploadFailed"/>
<fmt:message key="provider.providerpreference.signatureStamp.msgUploadError" var="_sigUploadError"/>
<fmt:message key="provider.providerpreference.signatureStamp.msgDrawFirst" var="_sigDrawFirst"/>
<fmt:message key="provider.providerpreference.signatureStamp.msgSaveSuccess" var="_sigSaveSuccess"/>
<fmt:message key="provider.providerpreference.signatureStamp.msgSaveFailed" var="_sigSaveFailed"/>
<fmt:message key="provider.providerpreference.signatureStamp.msgSaveError" var="_sigSaveError"/>
<fmt:message key="provider.providerpreference.signatureStamp.msgDeleteConfirm" var="_sigDeleteConfirm"/>
<fmt:message key="provider.providerpreference.signatureStamp.msgDeleteSuccess" var="_sigDeleteSuccess"/>
<fmt:message key="provider.providerpreference.signatureStamp.msgDeleteFailed" var="_sigDeleteFailed"/>
<fmt:message key="provider.providerpreference.signatureStamp.msgDeleteError" var="_sigDeleteError"/>
<fmt:message key="provider.providerpreference.signatureStamp.noSigUploaded" var="_sigNoSigUploaded"/>
<fmt:message key="provider.providerpreference.signatureStamp.btnDelete" var="_sigBtnDelete"/>
<script>
(function() {
    var sigStampUrl = '<%=request.getContextPath()%>/provider/providerSignatureStamp.do';

    var _msg = {
        selectFirst:    '<%=Encode.forJavaScript((String)pageContext.getAttribute("_sigSelectFirst"))%>',
        uploadSuccess:  '<%=Encode.forJavaScript((String)pageContext.getAttribute("_sigUploadSuccess"))%>',
        uploadFailed:   '<%=Encode.forJavaScript((String)pageContext.getAttribute("_sigUploadFailed"))%>',
        uploadError:    '<%=Encode.forJavaScript((String)pageContext.getAttribute("_sigUploadError"))%>',
        drawFirst:      '<%=Encode.forJavaScript((String)pageContext.getAttribute("_sigDrawFirst"))%>',
        saveSuccess:    '<%=Encode.forJavaScript((String)pageContext.getAttribute("_sigSaveSuccess"))%>',
        saveFailed:     '<%=Encode.forJavaScript((String)pageContext.getAttribute("_sigSaveFailed"))%>',
        saveError:      '<%=Encode.forJavaScript((String)pageContext.getAttribute("_sigSaveError"))%>',
        deleteConfirm:  '<%=Encode.forJavaScript((String)pageContext.getAttribute("_sigDeleteConfirm"))%>',
        deleteSuccess:  '<%=Encode.forJavaScript((String)pageContext.getAttribute("_sigDeleteSuccess"))%>',
        deleteFailed:   '<%=Encode.forJavaScript((String)pageContext.getAttribute("_sigDeleteFailed"))%>',
        deleteError:    '<%=Encode.forJavaScript((String)pageContext.getAttribute("_sigDeleteError"))%>',
        noSigUploaded:  '<%=Encode.forJavaScript((String)pageContext.getAttribute("_sigNoSigUploaded"))%>',
        btnDelete:      '<%=Encode.forJavaScript((String)pageContext.getAttribute("_sigBtnDelete"))%>'
    };

    // ── Canvas drawing ──
    var canvas = document.getElementById('sigCanvas');
    if (canvas) {
        var ctx = canvas.getContext('2d');
        var drawing = false;
        var hasDrawn = false;

        ctx.strokeStyle = '#000';
        ctx.lineWidth = 2.5;
        ctx.lineCap = 'round';
        ctx.lineJoin = 'round';

        function getPos(e) {
            var rect = canvas.getBoundingClientRect();
            var clientX, clientY;
            if (e.touches && e.touches.length > 0) {
                clientX = e.touches[0].clientX;
                clientY = e.touches[0].clientY;
            } else {
                clientX = e.clientX;
                clientY = e.clientY;
            }
            return {
                x: (clientX - rect.left) * (canvas.width / rect.width),
                y: (clientY - rect.top) * (canvas.height / rect.height)
            };
        }

        function startDraw(e) {
            e.preventDefault();
            drawing = true;
            hasDrawn = true;
            var pos = getPos(e);
            ctx.beginPath();
            ctx.moveTo(pos.x, pos.y);
        }

        function draw(e) {
            if (!drawing) return;
            e.preventDefault();
            var pos = getPos(e);
            ctx.lineTo(pos.x, pos.y);
            ctx.stroke();
        }

        function stopDraw(e) {
            if (drawing) {
                e.preventDefault();
                drawing = false;
            }
        }

        canvas.addEventListener('mousedown', startDraw);
        canvas.addEventListener('mousemove', draw);
        canvas.addEventListener('mouseup', stopDraw);
        canvas.addEventListener('mouseleave', stopDraw);
        canvas.addEventListener('touchstart', startDraw);
        canvas.addEventListener('touchmove', draw);
        canvas.addEventListener('touchend', stopDraw);
    }

    // ── Status message helper ──
    function showStatus(msg, type) {
        var el = document.getElementById('sigStatusMsg');
        if (!el) {
            return;
        }
        el.className = 'alert alert-' + type;
        el.textContent = msg;
        el.style.display = 'block';
        setTimeout(function() { el.style.display = 'none'; }, 4000);
    }

    function updatePreview(imageUrl) {
        if (!imageUrl) {
            return;
        }
        var img = document.getElementById('sigPreviewImg');
        var placeholder = document.getElementById('sigPlaceholder');
        img.src = imageUrl + (imageUrl.indexOf('?') >= 0 ? '&' : '?') + 't=' + Date.now();
        img.style.display = '';
        if (placeholder) placeholder.style.display = 'none';

        // Show delete button if not already present
        if (!document.getElementById('sigDeleteSection')) {
            var body = document.querySelector('#secSignatureStamp .accordion-body');
            var div = document.createElement('div');
            div.className = 'mb-0';
            div.id = 'sigDeleteSection';
            var btn = document.createElement('button');
            btn.type = 'button';
            btn.className = 'btn btn-sm btn-outline-danger';
            btn.onclick = window.deleteSignature;
            var icon = document.createElement('i');
            icon.className = 'fas fa-trash-alt';
            btn.appendChild(icon);
            btn.appendChild(document.createTextNode(' ' + _msg.btnDelete));
            div.appendChild(btn);
            body.appendChild(div);
        }
    }

    // ── XHR helper (CSRFGuard 4.5 auto-injects CSRF token into XMLHttpRequest) ──
    // Accepts FormData (multipart, for file uploads) or a plain params string (url-encoded).
    function sigXhr(body, onSuccess, onError) {
        var xhr = new XMLHttpRequest();
        xhr.open('POST', sigStampUrl, true);
        if (typeof body === 'string') {
            xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded');
        }
        xhr.onload = function() {
            if (xhr.status === 200) {
                try {
                    var data = JSON.parse(xhr.responseText);
                    onSuccess(data);
                } catch (e) {
                    onError();
                }
            } else {
                onError();
            }
        };
        xhr.onerror = onError;
        xhr.send(body);
    }

    // ── Upload file ──
    window.uploadSignatureFile = function() {
        var fileInput = document.getElementById('sigFileInput');
        if (!fileInput.files || fileInput.files.length === 0) {
            showStatus(_msg.selectFirst, 'warning');
            return;
        }
        var formData = new FormData();
        formData.append('image', fileInput.files[0]);
        formData.append('method', 'upload');

        sigXhr(formData, function(data) {
            if (data.success) {
                updatePreview(data.imageUrl);
                showStatus(_msg.uploadSuccess, 'success');
                fileInput.value = '';
            } else {
                showStatus(data.error || _msg.uploadFailed, 'danger');
            }
        }, function() { showStatus(_msg.uploadError, 'danger'); });
    };

    // ── Save drawn signature ──
    window.saveDrawnSignature = function() {
        if (!hasDrawn) {
            showStatus(_msg.drawFirst, 'warning');
            return;
        }
        var dataUrl = canvas.toDataURL('image/png');
        var params = 'method=saveDrawn&signatureData=' + encodeURIComponent(dataUrl);

        sigXhr(params, function(data) {
            if (data.success) {
                updatePreview(data.imageUrl);
                showStatus(_msg.saveSuccess, 'success');
                clearCanvas();
                hasDrawn = false;
            } else {
                showStatus(data.error || _msg.saveFailed, 'danger');
            }
        }, function() { showStatus(_msg.saveError, 'danger'); });
    };

    // ── Clear canvas ──
    window.clearCanvas = function() {
        if (canvas) {
            ctx.clearRect(0, 0, canvas.width, canvas.height);
            hasDrawn = false;
        }
    };

    // ── Delete signature ──
    window.deleteSignature = function() {
        if (!confirm(_msg.deleteConfirm)) return;

        var params = 'method=delete';

        sigXhr(params, function(data) {
            if (data.success) {
                var img = document.getElementById('sigPreviewImg');
                img.style.display = 'none';
                img.src = '';
                var placeholder = document.getElementById('sigPlaceholder');
                if (!placeholder) {
                    placeholder = document.createElement('span');
                    placeholder.id = 'sigPlaceholder';
                    placeholder.style.cssText = 'color:#999;font-style:italic;';
                    placeholder.textContent = _msg.noSigUploaded;
                    document.getElementById('sigPreviewArea').appendChild(placeholder);
                }
                placeholder.style.display = '';
                var delSection = document.getElementById('sigDeleteSection');
                if (delSection) delSection.remove();
                showStatus(_msg.deleteSuccess, 'success');
            } else {
                showStatus(data.error || _msg.deleteFailed, 'danger');
            }
        }, function() { showStatus(_msg.deleteError, 'danger'); });
    };
})();
</script>
</body>

</html>
