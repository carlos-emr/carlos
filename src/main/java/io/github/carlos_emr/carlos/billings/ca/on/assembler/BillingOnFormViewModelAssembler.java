/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.ca.on.assembler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingClaimHeaderDto;
import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingClaimItemDto;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingOnFormViewModel;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingAdmissionDateLoader;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingOnClaimLoader;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingOnLookupService;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingSiteIdService;
import io.github.carlos_emr.carlos.commn.dao.DxresearchDAO;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.IsPropertiesOn;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Dxresearch;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.ProviderPreference;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.web.admin.ProviderPreferencesUIBean;
import io.github.carlos_emr.carlos.billings.ca.on.support.BillingOnRequestParameters;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Orchestrator that assembles the {@link BillingOnFormViewModel} from
 * request parameters and DAO lookups. The actual data-loading work is
 * delegated to focused composers; this class handles request-param
 * normalization + threads the composers in the correct order.
 *
 * <p>Composer breakdown:</p>
 * <ul>
 *   <li>{@link BillingOnFormDemographicLoader} — demographic, age,
 *       referral doctor, validation messages</li>
 *   <li>the inlined {@link #recommendBillingGuidelines} step — Drools billing
 *       guidelines</li>
 *   <li>{@link BillingOnFormBillFormResolver} — {@code ctlBillForm}
 *       priority chain (curBillForm → roster → preference → group →
 *       default)</li>
 *   <li>{@link BillingOnFormServiceGridComposer} — service-code grid +
 *       billing-form menu + dx codes by type</li>
 * </ul>
 *
 * <p>The dependency-free helpers (DOB age math, id-token sanitisation) live in
 * {@link io.github.carlos_emr.carlos.billings.ca.on.support.BillingDateOfBirths}
 * and {@link io.github.carlos_emr.carlos.billings.ca.on.support.BillingDomIdTokens}
 * respectively.</p>
 *
 * @since 2026-04-24
 */
@org.springframework.stereotype.Service
public class BillingOnFormViewModelAssembler {

    // Direct refs only for the small bits the orchestrator still inlines
    // (provider list + dx + user-property lookup + billing-history); the
    // rest of the DAOs flow through the composers built in the constructor.
    private final DxresearchDAO dxresearchDao;
    private final UserPropertyDAO userPropertyDao;
    private final ProviderDao providerDao;
    private final BillingOnLookupService lookupService;
    private final BillingOnClaimLoader claimLoader;

    // Inner steps — built once per assembler instance.
    private final BillingOnFormDemographicLoader demographicLoader;
    private final BillingOnFormBillFormResolver billFormResolver;
    private final BillingOnFormServiceGridComposer serviceGridComposer;
    private final BillingOnFormSiteContextComposer siteContextComposer;
    private final BillingSiteIdService siteIdService;
    private final BillingAdmissionDateLoader admissionDateLoader;

    public BillingOnFormViewModelAssembler(DxresearchDAO dxresearchDao,
                               UserPropertyDAO userPropertyDao,
                               ProviderDao providerDao,
                               BillingOnLookupService lookupService,
                               BillingOnClaimLoader claimLoader,
                               BillingOnFormDemographicLoader demographicLoader,
                               BillingOnFormBillFormResolver billFormResolver,
                               BillingOnFormServiceGridComposer serviceGridComposer,
                               BillingOnFormSiteContextComposer siteContextComposer,
                               BillingSiteIdService siteIdService,
                               BillingAdmissionDateLoader admissionDateLoader) {
        this.dxresearchDao = dxresearchDao;
        this.userPropertyDao = userPropertyDao;
        this.providerDao = providerDao;
        this.lookupService = lookupService;
        this.claimLoader = claimLoader;
        this.demographicLoader = demographicLoader;
        this.billFormResolver = billFormResolver;
        this.serviceGridComposer = serviceGridComposer;
        this.siteContextComposer = siteContextComposer;
        this.siteIdService = siteIdService;
        this.admissionDateLoader = admissionDateLoader;
    }

    /**
     * Builds the view model for the main Ontario billing form. Matches the
     * scriptlet ordering in the original JSP so the resulting state is
     * equivalent to the expected page contract.
     */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    @SuppressWarnings("deprecation")
    public BillingOnFormViewModel assemble(HttpServletRequest request, LoggedInInfo loggedInInfo) {
        CarlosProperties oscarVars = CarlosProperties.getInstance();
        BillingOnFormViewModel.Builder b = BillingOnFormViewModel.builder();

        // ---- request param echoes + user / provider context ----

        // Prefer the authenticated provider from LoggedInInfo over the session
        // attribute. The session "user" attribute is only set once (in
        // Login2Action#doProviderLogin) and never modified elsewhere, so the
        // two should match. Pulling from the authenticated context keeps the
        // assembler consistent with the action's identity check.
        String userNo = loggedInInfo != null ? loggedInInfo.getLoggedInProviderNo() : null;
        if (userNo == null || userNo.isEmpty()) {
            userNo = (String) request.getSession().getAttribute("user");
            if (userNo != null && !userNo.isEmpty()) {
                MiscUtils.getLogger().warn(
                        "BillingOnFormViewModelAssembler: LoggedInInfo missing providerNo; "
                        + "falling back to session attribute \"user\"={}", userNo);
            }
        }
        b.userNo(userNo);

        // xml_provider ("providerNo|ohipNo" picker output) overrides
        // providerview when present.
        String providerView = BillingOnRequestParameters.extractProviderNo(
                request.getParameter("xml_provider"),
                request.getParameter("providerview"));
        b.providerView(providerView);

        String today = java.time.LocalDate.now()
                .format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE);
        b.today(today);

        boolean singleClick = "yes".equals(oscarVars.getProperty("onBillingSingleClick", ""));
        b.singleClickEnabled(singleClick);

        boolean hospitalBilling = false;
        b.hospitalBilling(hospitalBilling);

        String clinicView = hospitalBilling
                ? oscarVars.getProperty("clinic_hospital", "")
                : oscarVars.getProperty("clinic_view", "");
        String clinicNo = oscarVars.getProperty("clinic_no", "").trim();
        String visitType = hospitalBilling ? "02" : oscarVars.getProperty("visit_type", "");
        if (visitType.startsWith("00") || visitType.isEmpty()) {
            clinicView = "0000";
        }
        // NOTE: do NOT call b.clinicView(...) here — the field is finalized
        // below after xml_location resolution. Setting the builder twice
        // would just discard the first write.
        b.clinicNo(clinicNo);
        b.visitType(visitType);

        // Missing appointment_no defaults to "0" — the legacy convention for
        // a manual bill not tied to an existing appointment.
        String apptNo = firstNonNull(request.getParameter("appointment_no"), "0");
        if (apptNo.isEmpty()) {
            apptNo = "0";
        }
        b.appointmentNo(apptNo);

        String billReferenceDate;
        if ("0".equals(apptNo)) {
            billReferenceDate = firstNonNull(
                    request.getParameter("service_date"),
                    today);
        } else {
            billReferenceDate = request.getParameter("appointment_date");
        }
        // Defensive fallback: in the apptNo=="0" branch service_date may be
        // null and in the else branch appointment_date may be null. Either
        // way, downstream ConversionUtils.fromDateString and calculateAge
        // both NPE on null, so coalesce to today as the safe default.
        if (billReferenceDate == null || billReferenceDate.isEmpty()) {
            billReferenceDate = today;
        }
        b.billReferenceDate(billReferenceDate);

        // Coalesce request-param strings to empty rather than null. The JSP
        // and downstream JS string-builders call URLEncoder.encode(...) and
        // concat into href URLs, both of which NPE on null inputs.
        String demoName = nullToEmpty(request.getParameter("demographic_name"));
        String demoNo = nullToEmpty(request.getParameter("demographic_no"));
        String apptProviderNo = nullToEmpty(request.getParameter("apptProvider_no"));
        b.demoName(demoName);
        b.demographicNo(demoNo);
        b.apptProviderNo(apptProviderNo);

        String mReview = firstNonNull(request.getParameter("m_review"), "");
        b.mReview(mReview);
        b.ctlBillForm(nullToEmpty(request.getParameter("billForm")));
        b.curBillForm(nullToEmpty(request.getParameter("curBillForm")));

        // Manual billing loads (no appointment context) leave apptProvider_no
        // blank but carry the actual selection in xml_provider | providerview.
        // Without this fallback, providerPreference and defaultBillFormName
        // lookups below run against "" and the form opens against the wrong
        // provider's defaults.
        String providerNo;
        if (apptProviderNo.equalsIgnoreCase("none")) {
            providerNo = userNo;
        } else if (!apptProviderNo.isEmpty()) {
            providerNo = apptProviderNo;
        } else {
            String fromPicker = BillingOnRequestParameters.extractProviderNo(
                    request.getParameter("xml_provider"),
                    request.getParameter("providerview"));
            providerNo = !fromPicker.isEmpty() ? fromPicker : userNo;
        }
        b.providerNo(providerNo);

        // ---- demographic + age + referral + validation messages ----

        BillingOnFormDemographicLoader.LoadedDemographic loaded =
                demographicLoader.load(b, loggedInInfo, demoNo);
        Demographic demo = loaded.demo();
        String rosterStatus = loaded.rosterStatus();

        // Patient Dx + add/match codes for the dx-add UI panel.
        b.patientDx(loadPatientDx(demoNo));
        UserProperty addCodeProp = userPropertyDao.getProp(UserProperty.CODE_TO_ADD_PATIENTDX);
        String patientDxAddCode = addCodeProp != null ? nullToEmpty(addCodeProp.getValue()).trim() : "";
        UserProperty matchCodeProp = userPropertyDao.getProp(UserProperty.CODE_TO_MATCH_PATIENTDX);
        String patientDxMatchCode = matchCodeProp != null ? nullToEmpty(matchCodeProp.getValue()).trim() : "";
        b.patientDxAddCode(patientDxAddCode)
                .patientDxMatchCode(patientDxMatchCode);

        // ---- Drools billing recommendations ----
        recommendBillingGuidelines(b, loggedInInfo, demoNo, userNo);

        // ---- billing history (drives default dx + visitType resolution below) ----
        LoadedBillingHistory history = loadBillingHistory(demoNo);
        List<BillingOnFormViewModel.BillingHistoryEntry> historyEntries = history.entries();
        b.billingHistory(history.entries())
                .historyUnavailable(history.unavailable());

        // ---- provider list for the picker ----
        List<BillingOnFormViewModel.ProviderOption> providers = new ArrayList<>();
        for (Provider p : providerDao.getProvidersWithNonEmptyCredentials()) {
            providers.add(new BillingOnFormViewModel.ProviderOption(
                    nullToEmpty(p.getLastName()),
                    nullToEmpty(p.getFirstName()),
                    nullToEmpty(p.getProviderNo()) + "|" + nullToEmpty(p.getOhipNo())));
        }
        b.providers(providers);

        // ---- provider preference + dx-code/visit-type defaults ----
        ProviderPreference preference = (providerNo != null && !providerNo.isEmpty())
                ? ProviderPreferencesUIBean.getProviderPreferenceByProviderNo(providerNo)
                : null;

        // Default dx code: request param -> provider preference -> last-billed dx.
        String dxCodeParam = request.getParameter("dxCode");
        String dxCode = dxCodeParam != null && !dxCodeParam.isEmpty()
                ? dxCodeParam
                : (preference != null ? nullToEmpty(preference.getDefaultDxCode()) : "");
        if ((dxCode == null || dxCode.isEmpty()) && !historyEntries.isEmpty()) {
            dxCode = nullToEmpty(historyEntries.get(0).diagnosticCode());
        }
        b.dxCode(nullToEmpty(dxCode));

        // Default visit type: request param -> last-billed visit type -> existing visitType.
        String xmlVisitTypeParam = request.getParameter("xml_visittype");
        String xmlVisitType = xmlVisitTypeParam != null && !xmlVisitTypeParam.isEmpty()
                ? xmlVisitTypeParam
                : (!historyEntries.isEmpty() ? nullToEmpty(historyEntries.get(0).visitType()) : "");
        if (!xmlVisitType.isEmpty()) {
            visitType = xmlVisitType;
        }
        b.visitType(nullToEmpty(visitType))
                .xmlVisitType(nullToEmpty(xmlVisitType));

        // ---- ctlBillForm priority-chain resolution ----
        BillingOnFormBillFormResolver.ResolvedBillForm resolved = billFormResolver.resolve(
                b, request, visitType, rosterStatus, providerNo, userNo, apptProviderNo);
        String ctlBillForm = resolved.ctlBillForm();

        // ---- clinic view + visit date ----

        // Resolution order:
        //   1. user-selected xml_location query param wins (including "0000",
        //      which is a legitimate "no-location" selection in some installs);
        //   2. CarlosProperties.clinic_view is the per-installation default;
        //   3. "0000" is the final fallback if both are missing.
        String xmlLocationParam = request.getParameter("xml_location");
        String xmlLocation = xmlLocationParam != null && !xmlLocationParam.isEmpty()
                ? xmlLocationParam
                : "0000";
        String propertiesClinicView = CarlosProperties.getInstance().getProperty("clinic_view");
        if (xmlLocationParam != null && !xmlLocationParam.isEmpty()) {
            clinicView = xmlLocationParam;
        } else if (propertiesClinicView != null && !propertiesClinicView.isEmpty()) {
            clinicView = propertiesClinicView;
        } else {
            clinicView = "0000";
        }
        b.clinicView(nullToEmpty(clinicView))
                .xmlLocation(nullToEmpty(xmlLocation));

        // xml_vdate -> visitDate; empty if not explicitly supplied.
        String xmlVdateParam = request.getParameter("xml_vdate");
        String visitDate = xmlVdateParam != null ? xmlVdateParam : "";
        b.visitDate(visitDate);

        // ---- service-code grid + billing menu + dx codes by type ----
        java.util.Date filterDate = io.github.carlos_emr.carlos.util.ConversionUtils
                .fromDateString(billReferenceDate);
        if (request.getParameter("start_time") != null) {
            filterDate = io.github.carlos_emr.carlos.util.ConversionUtils
                    .fromTimestampString(billReferenceDate + " " + request.getParameter("start_time"));
        }
        java.util.Date billRefDate = io.github.carlos_emr.carlos.util.ConversionUtils
                .fromDateString(billReferenceDate);

        serviceGridComposer.compose(b, ctlBillForm, filterDate, billRefDate, demo);

        // site-context (multisite, RMA / clinic-nbr) loaded by the
        // dedicated composer instead of inline in the JSP.
        siteContextComposer.populate(b, request, userNo, apptProviderNo, apptNo);

        // ---- billing favourites (flat name/code list for the cutlist dropdown) ----
        List<String> favList = lookupService.getBillingFavouriteList();
        b.billingFavourites(favList == null ? Collections.emptyList() : favList);
        // Pair the alternating [text, value, text, value, ...] entries into a
        // structured list so the JSP can iterate via JSTL instead of stepping
        // by 2 in a scriptlet.
        List<BillingOnFormViewModel.BillingFavouriteOption> favOptions = new ArrayList<>();
        if (favList != null) {
            for (int i = 0; i + 1 < favList.size(); i += 2) {
                favOptions.add(new BillingOnFormViewModel.BillingFavouriteOption(
                        nullToEmpty(favList.get(i)), nullToEmpty(favList.get(i + 1))));
            }
        }
        b.billingFavouriteOptions(favOptions);

        // ---- recent-billing history rows (the bottom table renders aL/iAL pairs) ----
        LoadedBillingHistoryRows historyRows = loadHistoryRows(demoNo);
        b.billingHistoryRows(historyRows.rows());
        if (historyRows.unavailable()) {
            b.historyUnavailable(true);
        }

        // ---- visit-location dropdown (was inline tdbObj.facilityNumber()) ----
        List<String> facilityFlat = lookupService.getFacilty_num();
        List<BillingOnFormViewModel.FacilityNumOption> facilities = new ArrayList<>();
        if (facilityFlat != null) {
            for (int i = 0; i + 1 < facilityFlat.size(); i += 2) {
                facilities.add(new BillingOnFormViewModel.FacilityNumOption(
                        nullToEmpty(facilityFlat.get(i)),
                        nullToEmpty(facilityFlat.get(i + 1))));
            }
        }
        b.facilityNumOptions(facilities);

        // Default location for the dropdown's selected state. Mirrors the
        // legacy fallback chain: xml_location request param > last billed
        // clinic_ref_code from history > clinicView.
        String resolvedLocation;
        String xmlLocReq = request.getParameter("xml_location");
        if (xmlLocReq != null && !xmlLocReq.isEmpty()) {
            resolvedLocation = xmlLocReq;
        } else if (!historyEntries.isEmpty() && historyEntries.get(0).clinicRefCode() != null
                && !historyEntries.get(0).clinicRefCode().isEmpty()) {
            resolvedLocation = historyEntries.get(0).clinicRefCode();
        } else {
            resolvedLocation = clinicView == null ? "" : clinicView;
        }
        b.defaultLocation(nullToEmpty(resolvedLocation));

        // ---- legacy non-multisite "site" dropdown (BillingSiteIdService) ----
        if (!IsPropertiesOn.isMultisitesEnable()) {
            String scheduleSiteId = oscarVars.getProperty("scheduleSiteID", "");
            if (scheduleSiteId != null && !scheduleSiteId.isEmpty()) {
                try {
                    String[] siteList = siteIdService.getSiteList();
                    if (siteList != null && siteList.length > 0) {
                        String strServDate = firstNonNull(
                                request.getParameter("appointment_date"), today);
                        String thisSite = "";
                        String suggested = "";
                        thisSite = new io.github.carlos_emr.carlos.appt.JdbcApptImpl()
                                .getLocationFromSchedule(strServDate, apptProviderNo);
                        suggested = siteIdService.getSuggestSite(siteList, thisSite,
                                strServDate, apptProviderNo);
                        List<BillingOnFormViewModel.LegacySiteOption> siteOptions = new ArrayList<>();
                        String suggestedFinal = nullToEmpty(suggested);
                        for (String name : siteList) {
                            String n = nullToEmpty(name);
                            siteOptions.add(new BillingOnFormViewModel.LegacySiteOption(
                                    n, n.equals(suggestedFinal)));
                        }
                        b.legacySiteContextEnabled(true)
                                .legacySiteOptions(siteOptions);
                    }
                } catch (SecurityException sec) {
                    throw sec;
                } catch (RuntimeException e) {
                    b.siteContextDegraded(true);
                    MiscUtils.getLogger().warn( // NOSONAR javasecurity:S5145 - sanitized with LogSafe
                            "Site-suggest lookup failed for provider={}; rendering empty suggestion",
                            LogSafe.sanitize(apptProviderNo), e);
                }
            }
        }

        // ---- admission date pre-fill ----
        String admDate = "";
        String inPatient = oscarVars.getProperty("inPatient");
        if (inPatient != null && "yes".equalsIgnoreCase(inPatient.trim())
                && demoNo != null && !demoNo.isEmpty()) {
            try {
                admDate = nullToEmpty(admissionDateLoader.getAdmissionDate(loggedInInfo, demoNo));
            } catch (RuntimeException e) {
                MiscUtils.getLogger().error( // NOSONAR javasecurity:S5145 - sanitized with LogSafe
                        "Admission-date lookup failed for demo={}", LogSafe.sanitize(demoNo), e);
                b.admissionDateUnavailable(true);
            }
        }
        // Legacy override: hospital / nursing-home visit types pull the
        // admission date from the latest billing history.
        if (!historyEntries.isEmpty() && (visitType.startsWith("02") || visitType.startsWith("04"))) {
            String histVisitDate = nullToEmpty(historyEntries.get(0).visitDate());
            if (!histVisitDate.isEmpty()) {
                admDate = histVisitDate;
            }
        }
        b.admissionDate(admDate);

        // Pre-resolve the xml_vdate input value: request param takes precedence
        // over the assembler-computed admDate. This bakes the param-vs-default
        // selection into the model so the JSP renders a single
        // ${formModel.defaultXmlVdate} instead of an inline ternary on
        // ${param.xml_vdate}, which the encoder-validator hook flags as
        // XSS-sensitive even inside <c:choose><c:when>.
        String xmlVdateReq = request.getParameter("xml_vdate");
        b.defaultXmlVdate(xmlVdateReq != null && !xmlVdateReq.isEmpty()
                ? xmlVdateReq : admDate);

        // ---- selected bill type (for the xml_billtype dropdown) ----
        // Legacy logic: roster_status QU/FS (with defaultServiceType != RN)
        // forces PAT, then request param overrides.
        String defaultBillTypeForJsp = nullToEmpty(b.peekDefaultBillType());
        String defaultServiceTypeForRoster = nullToEmpty(b.peekDefaultServiceType());
        if (("QU - Quebec".equals(rosterStatus) || "FS".equals(rosterStatus))
                && !"RN".equals(defaultServiceTypeForRoster)) {
            defaultBillTypeForJsp = "PAT";
            b.defaultBillType(defaultBillTypeForJsp);
        }
        String reqBillType = request.getParameter("xml_billtype");
        b.selectedBillType(reqBillType != null && !reqBillType.isEmpty()
                ? reqBillType : defaultBillTypeForJsp);

        // ---- assigned billing physician display name (was providerBean session map) ----
        // The "providerBean" session attribute stores Provider.getFormattedName()
        // keyed by providerNo. Falls back to a fresh Provider lookup when the
        // session attribute hasn't been seeded yet.
        ResolvedAssgProviderDisplay assgProviderDisplay =
                resolveAssgProviderDisplay(request, apptProviderNo);
        b.assgProviderDisplay(assgProviderDisplay.value())
                .assignedProviderUnavailable(assgProviderDisplay.unavailable());

        // ---- referral checkbox + name/no defaults ----
        String rfCheckParam = request.getParameter("rfcheck");
        String refDocNameParam = request.getParameter("referralDocName");
        String refCodeParam = request.getParameter("referralCode");
        boolean refDefaultChecked = "checked".equals(
                oscarVars.getProperty("billingRefBoxDefault", ""));
        if (rfCheckParam != null) {
            b.referralCheckedDefault("checked".equals(rfCheckParam) ? "checked" : "")
                    .referralNameDefault(nullToEmpty(refDocNameParam))
                    .referralNoDefault(nullToEmpty(refCodeParam));
        } else if (refDefaultChecked) {
            b.referralCheckedDefault("checked")
                    .referralNameDefault(nullToEmpty(b.peekReferralDoctor()))
                    .referralNoDefault(nullToEmpty(b.peekReferralDoctorOhip()));
        } else {
            b.referralCheckedDefault("")
                    .referralNameDefault("")
                    .referralNoDefault("");
        }

        // ---- dx code default for the dxCode input (request param > model.dxCode) ----
        String reqDxCode = request.getParameter("dxCode");
        b.dxCodeDefault(reqDxCode != null && !reqDxCode.isEmpty()
                ? reqDxCode : nullToEmpty(b.peekDxCode()));

        // ---- service date default (only used when appt_no == "0") ----
        String reqServiceDate = request.getParameter("service_date");
        b.serviceDateDefault(reqServiceDate != null && !reqServiceDate.isEmpty()
                ? reqServiceDate : today);

        // ---- config props + URL-encoded demographic name for the
        // onChangePrivate JS click handler ----
        b.primaryCareIncentive(nullToEmpty(oscarVars.getProperty("primary_care_incentive", "")).trim())
                .defaultView(nullToEmpty(oscarVars.getProperty("default_view", "")).trim())
                .demoNameUrlEncoded(java.net.URLEncoder.encode(
                        nullToEmpty(demoName), java.nio.charset.StandardCharsets.UTF_8));

        // ---- multisite xml_provider default (request param > assembler default) ----
        String reqXmlProvider = request.getParameter("xml_provider");
        // The assembler-supplied default lives on the in-flight builder via the
        // siteContextComposer; re-read it for the final pre-resolve step.
        String composedDefault = b.peekDefaultXmlProvider();
        b.selectedXmlProvider(reqXmlProvider != null && !reqXmlProvider.isEmpty()
                ? reqXmlProvider : nullToEmpty(composedDefault));

        // ---- request-param echoes (form-state preservation across self-posts) ----
        java.util.Map<String, String> echoes = new java.util.HashMap<>();
        for (String name : new String[]{
                "appointment_date", "start_time", "asstProvider_no", "apptProvider_no",
                "billNo_old", "billStatus_old", "dxCode1", "dxCode2", "demographic_no",
                "appointment_no", "status", "services_checked"}) {
            String v = request.getParameter(name);
            if (v != null) echoes.put(name, v);
        }
        // Service-code grid (FIELD_SERVICE_NUM = 12 inputs).
        for (int i = 0; i < io.github.carlos_emr.carlos.billings.ca.on.support.BillingOnConstants.FIELD_SERVICE_NUM; i++) {
            for (String prefix : new String[]{"serviceCode", "serviceUnit", "serviceAt"}) {
                String name = prefix + i;
                String v = request.getParameter(name);
                if (v != null) echoes.put(name, v);
            }
        }
        // checkFlag (referenced by titlesearch hidden input).
        String checkFlag = request.getParameter("checkFlag");
        echoes.put("checkFlag", checkFlag != null ? checkFlag : "0");
        b.requestParamEchoes(echoes);

        // ---- display msg (errorMsg + warningMsg + DOB-invalid notice) ----
        StringBuilder msg = new StringBuilder("The default unit and @ value is 1.");
        msg.append(nullToEmpty(b.peekErrorMsg()));
        msg.append(nullToEmpty(b.peekWarningMsg()));
        if (b.peekDemoDobInvalid()) {
            msg.append("Warning: the patient's stored DOB is malformed; "
                    + "age-keyed premium codes and visit-type defaults are unreliable.");
        }
        b.displayMessage(msg.toString());

        return b.build();
    }

    /**
     * Resolves the "assigned billing physician" display string the legacy JSP
     * looked up via {@code providerBean.getProperty(assgProvider_no, "")}.
     * The session bean is populated by post-login flows; falls back to a
     * direct {@link ProviderDao} lookup for the truncate-or-full pattern when
     * the session map is empty (e.g., test / fresh-login paths).
     */
    private record ResolvedAssgProviderDisplay(String value, boolean unavailable) {
    }

    private ResolvedAssgProviderDisplay resolveAssgProviderDisplay(HttpServletRequest request, String apptProviderNo) {
        if (apptProviderNo == null || apptProviderNo.isEmpty()) return new ResolvedAssgProviderDisplay("", false);
        Object sessionBean = request.getSession().getAttribute("providerBean");
        String name = "";
        if (sessionBean instanceof java.util.Properties props) {
            name = props.getProperty(apptProviderNo, "");
        }
        if (name.isEmpty()) {
            try {
                Provider p = providerDao.getProvider(apptProviderNo);
                if (p != null) {
                    name = p.getFormattedName();
                }
            } catch (RuntimeException e) {
                MiscUtils.getLogger().warn( // NOSONAR javasecurity:S5145 - sanitized with LogSafe
                        "assgProvider display lookup failed for provider={}; rendering blank",
                        LogSafe.sanitize(apptProviderNo), e);
                return new ResolvedAssgProviderDisplay("", true);
            }
        }
        if (name == null) return new ResolvedAssgProviderDisplay("", false);
        return new ResolvedAssgProviderDisplay(
                name.length() > 15 ? name.substring(0, 14) : name, false);
    }

    /**
     * Loads the recent-billing history rows the JSP renders at the bottom of
     * the form. Up to 5 (claim, item) pairs from {@link BillingOnClaimLoader}.
     */
    private LoadedBillingHistoryRows loadHistoryRows(String demoNo) {
        List<BillingOnFormViewModel.BillingHistoryRow> rows = new ArrayList<>();
        if (demoNo == null || demoNo.isEmpty()) {
            return new LoadedBillingHistoryRows(rows, false);
        }
        boolean unavailable = false;
        try {
            List<Object> raw = claimLoader.getBillingHist(demoNo, 5, 0, null);
            for (int i = 0; i + 1 < raw.size(); i += 2) {
                io.github.carlos_emr.carlos.billings.ca.on.dto.BillingClaimHeaderDto header =
                        (io.github.carlos_emr.carlos.billings.ca.on.dto.BillingClaimHeaderDto) raw.get(i);
                io.github.carlos_emr.carlos.billings.ca.on.dto.BillingClaimItemDto item =
                        (io.github.carlos_emr.carlos.billings.ca.on.dto.BillingClaimItemDto) raw.get(i + 1);
                String updateDt = nullToEmpty(header.updateDateTime());
                rows.add(new BillingOnFormViewModel.BillingHistoryRow(
                        nullToEmpty(header.getId()),
                        nullToEmpty(header.billingDate()),
                        nullToEmpty(item.serviceDate()),
                        nullToEmpty(item.serviceCode()),
                        nullToEmpty(item.getDx()),
                        updateDt.length() >= 10 ? updateDt.substring(0, 10) : updateDt));
            }
        } catch (RuntimeException rtEx) {
            unavailable = true;
            MiscUtils.getLogger().error( // NOSONAR javasecurity:S5145 - sanitized with LogSafe
                    "Billing history rows lookup failed for demo={}; rendering with empty history",
                    LogSafe.sanitize(demoNo), rtEx);
        }
        return new LoadedBillingHistoryRows(rows, unavailable);
    }

    private record LoadedBillingHistoryRows(
            List<BillingOnFormViewModel.BillingHistoryRow> rows,
            boolean unavailable) {
    }

    /**
     * Result of attempting to parse a YYYYMMDD DOB string into an age in
     * years. {@code invalid} is true when the input was non-empty but failed
     * to parse — the assembler propagates the flag onto the view model so
     * the JSP can surface a banner instead of silently emitting a 0-year-old.
     *
     * <p>The {@code age == 0 && !invalid} state legitimately means "no DOB
     * supplied / no patient context yet". Only {@code invalid == true}
     * indicates a parse failure worth warning about. The compact constructor
     * rejects the contradictory {@code invalid && age != 0} state so a
     * future caller can't fabricate a "partially-computed but flagged"
     * result.</p>
     */
    /**
     * Inlined Drools billing-guidelines step (formerly the
     * {@code BillingONFormDroolsRecommender} composer — collapsed inline
     * because it was used by exactly one assembler and not reusable per
     * the package's scope contract). Catches broad {@code Exception} because
     * Drools rule compilation can throw {@code RuntimeException},
     * {@code KieBaseException}, or other rule-cache failures; the form must
     * still render if billing-guideline evaluation is unavailable.
     */
    private void recommendBillingGuidelines(BillingOnFormViewModel.Builder b,
                                             LoggedInInfo loggedInInfo,
                                             String demoNo,
                                             String userNo) {
        StringBuilder recommendations = new StringBuilder();
        try {
            List<io.github.carlos_emr.carlos.decisionSupport.model.DSConsequence> consequences =
                    io.github.carlos_emr.carlos.billings.ca.bc.decisionSupport.BillingGuidelines
                            .getInstance()
                            .evaluateAndGetConsequences(loggedInInfo, demoNo, userNo);
            for (io.github.carlos_emr.carlos.decisionSupport.model.DSConsequence dscon : consequences) {
                if (dscon.getConsequenceStrength()
                        == io.github.carlos_emr.carlos.decisionSupport.model.DSConsequence.ConsequenceStrength.warning) {
                    recommendations.append(io.github.carlos_emr.carlos.utility.SafeEncode
                            .forHtml(dscon.getText())).append("<br/>");
                }
            }
        } catch (Exception e) {
            MiscUtils.getLogger().error(
                    "Drools billing-guidelines evaluation failed; rendering without recommendations",
                    e);
            b.recommendationsUnavailable(true);
        }
        b.billingRecommendations(recommendations.toString());
    }

    /**
     * Loads the patient's ICD-9 diagnosis list. Pure read; no side effects.
     */
    private List<String> loadPatientDx(String demoNo) {
        List<String> patientDx = new ArrayList<>();
        if (demoNo == null || demoNo.isEmpty()) {
            return patientDx;
        }
        try {
            List<Dxresearch> dxList = dxresearchDao.getByDemographicNo(Integer.parseInt(demoNo));
            for (Dxresearch dx : dxList) {
                if ("icd9".equals(dx.getCodingSystem())) {
                    patientDx.add(dx.getDxresearchCode());
                }
            }
        } catch (NumberFormatException nfe) {
            MiscUtils.getLogger().warn( // NOSONAR javasecurity:S5145 - sanitized with LogSafe
                    "Invalid demographic_no for dx lookup: {}",
                    LogSafe.sanitize(demoNo), nfe);
        }
        return patientDx;
    }

    /**
     * Loads up to 5 recent billing-history entries via BillingOnClaimLoader.
     * The JSP only reads the first record's visitType / clinic_ref_code, so
     * we surface only that. Split exception handling: ClassCastException is
     * loud at ERROR (data-shape regression in BillingOnClaimLoader);
     * RuntimeException is also ERROR (DB outage stripping the visit-context
     * hint is high-impact for clinical workflow — provider may
     * duplicate-bill the same encounter).
     */
    private LoadedBillingHistory loadBillingHistory(String demoNo) {
        List<BillingOnFormViewModel.BillingHistoryEntry> history = new ArrayList<>();
        if (demoNo == null || demoNo.isEmpty()) {
            return new LoadedBillingHistory(history, false);
        }
        boolean unavailable = false;
        try {
            List<Object> raw = claimLoader.getBillingHist(demoNo, 5, 0, null);
            if (raw.size() >= 2) {
                BillingClaimHeaderDto header = (BillingClaimHeaderDto) raw.get(0);
                BillingClaimItemDto item = (BillingClaimItemDto) raw.get(1);
                history.add(new BillingOnFormViewModel.BillingHistoryEntry(
                        nullToEmpty(header.admissionDate()),
                        nullToEmpty(header.visitType()),
                        nullToEmpty(header.facilityNumber()),
                        nullToEmpty(item.getDx())));
            }
        } catch (ClassCastException ccEx) {
            unavailable = true;
            MiscUtils.getLogger().error( // NOSONAR javasecurity:S5145 - sanitized with LogSafe
                    "Billing history data-shape regression for demo={} — BillingOnClaimLoader returned unexpected types",
                    LogSafe.sanitize(demoNo), ccEx);
        } catch (RuntimeException rtEx) {
            unavailable = true;
            MiscUtils.getLogger().error( // NOSONAR javasecurity:S5145 - sanitized with LogSafe
                    "Billing history lookup failed for demo={}; rendering with empty history",
                    LogSafe.sanitize(demoNo), rtEx);
        }
        return new LoadedBillingHistory(history, unavailable);
    }

    private record LoadedBillingHistory(
            List<BillingOnFormViewModel.BillingHistoryEntry> entries,
            boolean unavailable) {
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /** OWASP-style HTML body escape for the pre-rendered multisite-provider
     *  HTML strings. Defers to {@link io.github.carlos_emr.carlos.utility.SafeEncode}
     *  so the rules match the JSP's {@code <carlos:encode>} tag. */
    private static String escapeForHtml(String s) {
        return io.github.carlos_emr.carlos.utility.SafeEncode.forHtml(s);
    }

    /** OWASP-style HTML attribute escape for the {@code <option value='...'>}
     *  attribute the multisite-provider HTML emits. */
    private static String escapeForHtmlAttr(String s) {
        return io.github.carlos_emr.carlos.utility.SafeEncode.forHtmlAttribute(s);
    }

    private static String firstNonNull(String primary, String fallback) {
        return primary != null ? primary : fallback;
    }
}
