/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.ca.on.pageUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.billing.CA.filters.CodeFilterManager;
import io.github.carlos_emr.carlos.billings.ca.on.data.BillingClaimHeader1Data;
import io.github.carlos_emr.carlos.billings.ca.on.data.BillingItemData;
import io.github.carlos_emr.carlos.billings.ca.on.data.BillingONFormViewModel;
import io.github.carlos_emr.carlos.billings.ca.on.data.JdbcBillingReviewImpl;
import io.github.carlos_emr.carlos.commn.dao.BillingServiceDao;
import io.github.carlos_emr.carlos.commn.dao.CSSStylesDAO;
import io.github.carlos_emr.carlos.commn.dao.CtlBillingServiceDao;
import io.github.carlos_emr.carlos.commn.dao.CtlBillingServicePremiumDao;
import io.github.carlos_emr.carlos.commn.dao.CtlBillingTypeDao;
import io.github.carlos_emr.carlos.commn.dao.DiagnosticCodeDao;
import io.github.carlos_emr.carlos.commn.dao.DxresearchDAO;
import io.github.carlos_emr.carlos.commn.dao.MyGroupDao;
import io.github.carlos_emr.carlos.commn.dao.ProfessionalSpecialistDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderPreferenceDao;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Dxresearch;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.ProviderPreference;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.LogSanitizer;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.web.admin.ProviderPreferencesUIBean;

/**
 * Orchestrator that assembles the {@link BillingONFormViewModel} from
 * request parameters and DAO lookups. The actual data-loading work is
 * delegated to focused composers; this class handles request-param
 * normalization + threads the composers in the correct order.
 *
 * <p>Composer breakdown:</p>
 * <ul>
 *   <li>{@link BillingONFormDemographicLoader} — demographic, age,
 *       referral doctor, validation messages</li>
 *   <li>{@link BillingONFormDroolsRecommender} — Drools billing
 *       guidelines</li>
 *   <li>{@link BillingONFormBillFormResolver} — {@code ctlBillForm}
 *       priority chain (curBillForm → roster → preference → group →
 *       default)</li>
 *   <li>{@link BillingONFormServiceGridComposer} — service-code grid +
 *       billing-form menu + dx codes by type (the ~120-line block in
 *       the legacy implementation)</li>
 * </ul>
 *
 * <p>Pre-refactor, every concern lived inline in a 600-line
 * {@code assemble} method. The split brings the orchestrator to ~250
 * lines and lets each concern be unit-tested independently. The static
 * helpers ({@link #calculateAge}, {@link #sanitizeIdToken},
 * {@link AgeResult}) remain on this class because the existing test
 * surface ({@code BillingONFormDataAssemblerCalculateAgeUnitTest},
 * {@code BillingONFormDataAssemblerSanitizeIdTokenUnitTest}) targets
 * them by name and the composers reference them via static imports.</p>
 *
 * @since 2026-04-24
 */
public final class BillingONFormDataAssembler {

    // Direct refs only for the small bits the orchestrator still inlines
    // (provider list + dx + user-property lookup + billing-history); the
    // rest of the DAOs flow through the composers built in the constructor.
    private final DxresearchDAO dxresearchDao;
    private final UserPropertyDAO userPropertyDao;
    private final ProviderDao providerDao;

    // Composers — built once per assembler instance.
    private final BillingONFormDemographicLoader demographicLoader;
    private final BillingONFormDroolsRecommender droolsRecommender;
    private final BillingONFormBillFormResolver billFormResolver;
    private final BillingONFormServiceGridComposer serviceGridComposer;

    /**
     * Production constructor used by Struts; resolves every dependency from
     * the Spring context via {@link SpringUtils#getBean}. Tests use the
     * package-private constructor below to inject mocks directly.
     */
    public BillingONFormDataAssembler() {
        this(SpringUtils.getBean(DemographicManager.class),
             SpringUtils.getBean(ProfessionalSpecialistDao.class),
             SpringUtils.getBean(DxresearchDAO.class),
             SpringUtils.getBean(UserPropertyDAO.class),
             SpringUtils.getBean(ProviderDao.class),
             SpringUtils.getBean(CtlBillingServiceDao.class),
             SpringUtils.getBean(ProviderPreferenceDao.class),
             SpringUtils.getBean(MyGroupDao.class),
             SpringUtils.getBean(BillingServiceDao.class),
             SpringUtils.getBean(CtlBillingServicePremiumDao.class),
             SpringUtils.getBean(CSSStylesDAO.class),
             SpringUtils.getBean(CodeFilterManager.class),
             SpringUtils.getBean(CtlBillingTypeDao.class),
             SpringUtils.getBean(DiagnosticCodeDao.class));
    }

    BillingONFormDataAssembler(DemographicManager demographicManager,
                               ProfessionalSpecialistDao professionalSpecialistDao,
                               DxresearchDAO dxresearchDao,
                               UserPropertyDAO userPropertyDao,
                               ProviderDao providerDao,
                               CtlBillingServiceDao ctlBillingServiceDao,
                               ProviderPreferenceDao providerPreferenceDao,
                               MyGroupDao myGroupDao,
                               BillingServiceDao billingServiceDao,
                               CtlBillingServicePremiumDao ctlBillingServicePremiumDao,
                               CSSStylesDAO cssStylesDao,
                               CodeFilterManager codeFilterManager,
                               CtlBillingTypeDao ctlBillingTypeDao,
                               DiagnosticCodeDao diagnosticCodeDao) {
        // Hold only what the orchestrator still consumes directly.
        this.dxresearchDao = dxresearchDao;
        this.userPropertyDao = userPropertyDao;
        this.providerDao = providerDao;

        // The remaining DAOs flow into the composers and aren't held here.
        this.demographicLoader = new BillingONFormDemographicLoader(
                demographicManager, professionalSpecialistDao);
        this.droolsRecommender = new BillingONFormDroolsRecommender();
        this.billFormResolver = new BillingONFormBillFormResolver(
                ctlBillingServiceDao, providerPreferenceDao, myGroupDao);
        this.serviceGridComposer = new BillingONFormServiceGridComposer(
                ctlBillingServiceDao, billingServiceDao, ctlBillingServicePremiumDao,
                cssStylesDao, codeFilterManager, ctlBillingTypeDao, diagnosticCodeDao);
    }

    /**
     * Builds the view model for the main Ontario billing form. Matches the
     * scriptlet ordering in the original JSP so the resulting state is
     * equivalent to the pre-refactor page.
     */
    public BillingONFormViewModel assemble(LoggedInInfo loggedInInfo, HttpServletRequest request) {
        CarlosProperties oscarVars = CarlosProperties.getInstance();
        BillingONFormViewModel.Builder b = BillingONFormViewModel.builder();

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
                        "BillingONFormDataAssembler: LoggedInInfo missing providerNo; "
                        + "falling back to session attribute \"user\"={}", userNo);
            }
        }
        b.userNo(userNo);

        // xml_provider ("providerNo|ohipNo" picker output) overrides
        // providerview when present.
        String providerView = BillingONRequestParams.extractProviderNo(
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
            String fromPicker = BillingONRequestParams.extractProviderNo(
                    request.getParameter("xml_provider"),
                    request.getParameter("providerview"));
            providerNo = !fromPicker.isEmpty() ? fromPicker : userNo;
        }
        b.providerNo(providerNo);

        // ---- demographic + age + referral + validation messages ----

        BillingONFormDemographicLoader.LoadedDemographic loaded =
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
        droolsRecommender.recommend(b, loggedInInfo, demoNo, userNo);

        // ---- billing history (drives default dx + visitType resolution below) ----
        List<BillingONFormViewModel.BillingHistoryEntry> history = loadBillingHistory(demoNo);
        b.billingHistory(history);

        // ---- provider list for the picker ----
        List<BillingONFormViewModel.ProviderOption> providers = new ArrayList<>();
        for (Provider p : providerDao.getProvidersWithNonEmptyCredentials()) {
            providers.add(new BillingONFormViewModel.ProviderOption(
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
        if ((dxCode == null || dxCode.isEmpty()) && !history.isEmpty()) {
            dxCode = nullToEmpty(history.get(0).diagnosticCode());
        }
        b.dxCode(nullToEmpty(dxCode));

        // Default visit type: request param -> last-billed visit type -> existing visitType.
        String xmlVisitTypeParam = request.getParameter("xml_visittype");
        String xmlVisitType = xmlVisitTypeParam != null && !xmlVisitTypeParam.isEmpty()
                ? xmlVisitTypeParam
                : (!history.isEmpty() ? nullToEmpty(history.get(0).visitType()) : "");
        if (!xmlVisitType.isEmpty()) {
            visitType = xmlVisitType;
        }
        b.visitType(nullToEmpty(visitType))
                .xmlVisitType(nullToEmpty(xmlVisitType));

        // ---- ctlBillForm priority-chain resolution ----
        BillingONFormBillFormResolver.ResolvedBillForm resolved = billFormResolver.resolve(
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

        // ---- billing favourites (flat name/code list for the cutlist dropdown) ----
        io.github.carlos_emr.carlos.billings.ca.on.data.JdbcBillingPageUtil favPageUtil =
                new io.github.carlos_emr.carlos.billings.ca.on.data.JdbcBillingPageUtil();
        @SuppressWarnings("unchecked")
        List<String> favList = favPageUtil.getBillingFavouriteList();
        b.billingFavourites(favList == null ? Collections.emptyList() : favList);

        return b.build();
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
    record AgeResult(int age, boolean invalid) {
        AgeResult {
            if (age < 0) {
                throw new IllegalArgumentException("age must be >= 0; got " + age);
            }
            if (invalid && age != 0) {
                throw new IllegalArgumentException(
                        "invalid=true must imply age==0; got age=" + age);
            }
        }
    }

    static AgeResult calculateAge(String dobYyyymmdd) {
        if (dobYyyymmdd == null || dobYyyymmdd.isEmpty()) {
            // Empty DOB is the "no patient yet" case, not a parse failure.
            return new AgeResult(0, false);
        }
        // PHI hygiene: never log the DOB itself. Logging length is enough
        // for ops to confirm the data shape regression without leaking
        // patient data per CLAUDE.md.
        if (dobYyyymmdd.length() != 8) {
            MiscUtils.getLogger().warn(
                    "calculateAge: DOB has length {} (expected 8); flagging invalid",
                    dobYyyymmdd.length());
            return new AgeResult(0, true);
        }
        try {
            int year = Integer.parseInt(dobYyyymmdd.substring(0, 4));
            int month = Integer.parseInt(dobYyyymmdd.substring(4, 6));
            int day = Integer.parseInt(dobYyyymmdd.substring(6, 8));
            java.time.LocalDate dob = java.time.LocalDate.of(year, month, day);
            return new AgeResult(
                    java.time.Period.between(dob, java.time.LocalDate.now()).getYears(),
                    false);
        } catch (NumberFormatException | java.time.DateTimeException e) {
            // A malformed DOB that survived the length()==8 guard (e.g.,
            // "99999999" parses but throws DateTimeException at LocalDate.of)
            // would silently emit a 0-year-old patient on the form, which
            // drives downstream visit-type defaults and premium codes off
            // bad input. Flag invalid so the JSP renders a warning banner.
            MiscUtils.getLogger().warn(
                    "calculateAge: 8-char DOB rejected by LocalDate.of ({}); flagging invalid",
                    e.getClass().getSimpleName());
            return new AgeResult(0, true);
        }
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
            MiscUtils.getLogger().warn(
                    "Invalid demographic_no for dx lookup: {}",
                    LogSanitizer.sanitize(demoNo));
        }
        return patientDx;
    }

    /**
     * Loads up to 5 recent billing-history entries via JdbcBillingReviewImpl.
     * The JSP only reads the first record's visitType / clinic_ref_code, so
     * we surface only that. Split exception handling: ClassCastException is
     * loud at ERROR (data-shape regression in JdbcBillingReviewImpl);
     * RuntimeException is also ERROR (DB outage stripping the visit-context
     * hint is high-impact for clinical workflow — provider may
     * duplicate-bill the same encounter).
     */
    private List<BillingONFormViewModel.BillingHistoryEntry> loadBillingHistory(String demoNo) {
        List<BillingONFormViewModel.BillingHistoryEntry> history = new ArrayList<>();
        if (demoNo == null || demoNo.isEmpty()) {
            return history;
        }
        try {
            JdbcBillingReviewImpl reviewer = new JdbcBillingReviewImpl();
            List<Object> raw = reviewer.getBillingHist(demoNo, 5, 0, null);
            if (raw.size() >= 2) {
                BillingClaimHeader1Data header = (BillingClaimHeader1Data) raw.get(0);
                BillingItemData item = (BillingItemData) raw.get(1);
                history.add(new BillingONFormViewModel.BillingHistoryEntry(
                        nullToEmpty(header.getAdmission_date()),
                        nullToEmpty(header.getVisittype()),
                        nullToEmpty(header.getFacilty_num()),
                        nullToEmpty(item.getDx())));
            }
        } catch (ClassCastException ccEx) {
            MiscUtils.getLogger().error(
                    "Billing history data-shape regression for demo={} — JdbcBillingReviewImpl returned unexpected types",
                    LogSanitizer.sanitize(demoNo), ccEx);
        } catch (RuntimeException rtEx) {
            MiscUtils.getLogger().error(
                    "Billing history lookup failed for demo={}; rendering with empty history",
                    LogSanitizer.sanitize(demoNo), rtEx);
        }
        return history;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /**
     * Reduces a DB-supplied service-type / bill-form code to a safe HTML id
     * token. Replaces every character outside {@code [A-Za-z0-9_-]} with
     * {@code _}. Service-type codes are conventionally short alphanumerics
     * in the {@code ctl_billservice} table, so this is a no-op in practice;
     * it exists to keep the rendered DOM ids well-formed if a malformed row
     * ever makes it into the table. Package-private to allow direct
     * unit-testing of the regex behavior.
     */
    static String sanitizeIdToken(String s) {
        if (s == null) {
            return "";
        }
        return s.replaceAll("[^A-Za-z0-9_-]", "_");
    }

    private static String firstNonNull(String primary, String fallback) {
        return primary != null ? primary : fallback;
    }
}
