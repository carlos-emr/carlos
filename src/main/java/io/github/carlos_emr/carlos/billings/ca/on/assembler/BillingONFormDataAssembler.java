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
package io.github.carlos_emr.carlos.billings.ca.on.assembler;

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
import io.github.carlos_emr.carlos.commn.IsPropertiesOn;
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
import io.github.carlos_emr.carlos.billings.ca.on.pageUtil.BillingONRequestParams;
import io.github.carlos_emr.carlos.billings.ca.on.pageUtil.BillingSiteIdPrep;

/**
 * Orchestrator that assembles the {@link BillingONFormViewModel} from
 * request parameters and DAO lookups. The actual data-loading work is
 * delegated to focused composers; this class handles request-param
 * normalization + threads the composers in the correct order.
 *
 * <p>Composer breakdown:</p>
 * <ul>
 *   <li>{@link BillingONFormDemographicStep} — demographic, age,
 *       referral doctor, validation messages</li>
 *   <li>the inlined {@link #recommendBillingGuidelines} step — Drools billing
 *       guidelines</li>
 *   <li>{@link BillingONFormBillFormStep} — {@code ctlBillForm}
 *       priority chain (curBillForm → roster → preference → group →
 *       default)</li>
 *   <li>{@link BillingONFormServiceGridStep} — service-code grid +
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

    // Inner steps — built once per assembler instance.
    private final BillingONFormDemographicStep demographicLoader;
    private final BillingONFormBillFormStep billFormResolver;
    private final BillingONFormServiceGridStep serviceGridComposer;
    private final BillingONFormSiteContextComposer siteContextComposer;

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
             SpringUtils.getBean(DiagnosticCodeDao.class),
             SpringUtils.getBean(io.github.carlos_emr.carlos.commn.dao.SiteDao.class),
             SpringUtils.getBean(io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao.class),
             SpringUtils.getBean(io.github.carlos_emr.carlos.commn.dao.ClinicNbrDao.class));
    }

    /** Test-friendly ctor with the legacy 14-DAO arg list. Site-context fields default to empty. */
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
        this(demographicManager, professionalSpecialistDao, dxresearchDao, userPropertyDao,
             providerDao, ctlBillingServiceDao, providerPreferenceDao, myGroupDao,
             billingServiceDao, ctlBillingServicePremiumDao, cssStylesDao, codeFilterManager,
             ctlBillingTypeDao, diagnosticCodeDao, null, null, null);
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
                               DiagnosticCodeDao diagnosticCodeDao,
                               io.github.carlos_emr.carlos.commn.dao.SiteDao siteDao,
                               io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao oscarAppointmentDao,
                               io.github.carlos_emr.carlos.commn.dao.ClinicNbrDao clinicNbrDao) {
        // Hold only what the orchestrator still consumes directly.
        this.dxresearchDao = dxresearchDao;
        this.userPropertyDao = userPropertyDao;
        this.providerDao = providerDao;

        // The remaining DAOs flow into the inner steps and aren't held here.
        this.demographicLoader = new BillingONFormDemographicStep(
                demographicManager, professionalSpecialistDao);
        this.billFormResolver = new BillingONFormBillFormStep(
                ctlBillingServiceDao, providerPreferenceDao, myGroupDao);
        this.serviceGridComposer = new BillingONFormServiceGridStep(
                ctlBillingServiceDao, billingServiceDao, ctlBillingServicePremiumDao,
                cssStylesDao, codeFilterManager, ctlBillingTypeDao, diagnosticCodeDao);
        this.siteContextComposer = new BillingONFormSiteContextComposer(
                siteDao, oscarAppointmentDao, clinicNbrDao, providerDao);
    }

    /**
     * Builds the view model for the main Ontario billing form. Matches the
     * scriptlet ordering in the original JSP so the resulting state is
     * equivalent to the pre-refactor page.
     */
    @SuppressWarnings("deprecation")
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

        BillingONFormDemographicStep.LoadedDemographic loaded =
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
        BillingONFormBillFormStep.ResolvedBillForm resolved = billFormResolver.resolve(
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

        // Round-15: site-context (multisite, RMA / clinic-nbr) loaded by the
        // dedicated composer instead of inline in the JSP.
        siteContextComposer.populate(b, request, userNo, apptProviderNo, apptNo);

        // ---- billing favourites (flat name/code list for the cutlist dropdown) ----
        io.github.carlos_emr.carlos.billings.ca.on.data.JdbcBillingPageUtil pageUtil =
                new io.github.carlos_emr.carlos.billings.ca.on.data.JdbcBillingPageUtil();
        List<String> favList = pageUtil.getBillingFavouriteList();
        b.billingFavourites(favList == null ? Collections.emptyList() : favList);
        // Pair the alternating [text, value, text, value, ...] entries into a
        // structured list so the JSP can iterate via JSTL instead of stepping
        // by 2 in a scriptlet.
        List<BillingONFormViewModel.BillingFavouriteOption> favOptions = new ArrayList<>();
        if (favList != null) {
            for (int i = 0; i + 1 < favList.size(); i += 2) {
                favOptions.add(new BillingONFormViewModel.BillingFavouriteOption(
                        nullToEmpty(favList.get(i)), nullToEmpty(favList.get(i + 1))));
            }
        }
        b.billingFavouriteOptions(favOptions);

        // ---- recent-billing history rows (the bottom table renders aL/iAL pairs) ----
        b.billingHistoryRows(loadHistoryRows(demoNo));

        // ---- visit-location dropdown (was inline tdbObj.getFacilty_num()) ----
        List<String> facilityFlat = pageUtil.getFacilty_num();
        List<BillingONFormViewModel.FacilityNumOption> facilities = new ArrayList<>();
        if (facilityFlat != null) {
            for (int i = 0; i + 1 < facilityFlat.size(); i += 2) {
                facilities.add(new BillingONFormViewModel.FacilityNumOption(
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
        } else if (!history.isEmpty() && history.get(0).clinicRefCode() != null
                && !history.get(0).clinicRefCode().isEmpty()) {
            resolvedLocation = history.get(0).clinicRefCode();
        } else {
            resolvedLocation = clinicView == null ? "" : clinicView;
        }
        b.defaultLocation(nullToEmpty(resolvedLocation));

        // ---- legacy non-multisite "site" dropdown (BillingSiteIdPrep) ----
        if (!IsPropertiesOn.isMultisitesEnable()) {
            String scheduleSiteId = oscarVars.getProperty("scheduleSiteID", "");
            if (scheduleSiteId != null && !scheduleSiteId.isEmpty()) {
                BillingSiteIdPrep sitePrep = new BillingSiteIdPrep();
                String[] siteList = sitePrep.getSiteList();
                if (siteList != null && siteList.length > 0) {
                    String strServDate = firstNonNull(
                            request.getParameter("appointment_date"), today);
                    String thisSite = "";
                    String suggested = "";
                    try {
                        thisSite = new io.github.carlos_emr.carlos.appt.JdbcApptImpl()
                                .getLocationFromSchedule(strServDate, apptProviderNo);
                        suggested = sitePrep.getSuggestSite(siteList, thisSite,
                                strServDate, apptProviderNo);
                    } catch (RuntimeException e) {
                        MiscUtils.getLogger().warn(
                                "Site-suggest lookup failed for provider={}; rendering empty suggestion",
                                LogSanitizer.sanitize(apptProviderNo), e);
                    }
                    List<BillingONFormViewModel.LegacySiteOption> siteOptions = new ArrayList<>();
                    String suggestedFinal = nullToEmpty(suggested);
                    for (String name : siteList) {
                        String n = nullToEmpty(name);
                        siteOptions.add(new BillingONFormViewModel.LegacySiteOption(
                                n, n.equals(suggestedFinal)));
                    }
                    b.legacySiteContextEnabled(true)
                            .legacySiteOptions(siteOptions);
                }
            }
        }

        // ---- admission date pre-fill ----
        String admDate = "";
        String inPatient = oscarVars.getProperty("inPatient");
        if (inPatient != null && "yes".equalsIgnoreCase(inPatient.trim())
                && demoNo != null && !demoNo.isEmpty()) {
            try {
                admDate = nullToEmpty(new io.github.carlos_emr.carlos.demographic.data.DemographicData()
                        .getDemographicDateJoined(loggedInInfo, demoNo));
            } catch (RuntimeException e) {
                MiscUtils.getLogger().error(
                        "Admission-date lookup failed for demo={}", LogSanitizer.sanitize(demoNo), e);
            }
        }
        // Legacy override: hospital / nursing-home visit types pull the
        // admission date from the latest billing history.
        if (!history.isEmpty() && (visitType.startsWith("02") || visitType.startsWith("04"))) {
            String histVisitDate = nullToEmpty(history.get(0).visitDate());
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
        // potential XSS even inside <c:choose><c:when>.
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
        b.assgProviderDisplay(resolveAssgProviderDisplay(request, apptProviderNo));

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

        // ---- multisite per-site provider <option>... HTML for the JS map ----
        // The legacy JSP iterated SiteDao.getActiveSitesByProviderNo and built
        // a JS associative array {siteName: "<option ...>...</option>..."}
        // inline. We pre-render it here so the JSP renders one
        // <c:forEach> JS-string assignment instead of a 50-line scriptlet.
        java.util.Map<String, String> siteHtml = new java.util.LinkedHashMap<>();
        for (BillingONFormViewModel.MultisiteSite site : b.peekMultisiteSites()) {
            StringBuilder html = new StringBuilder();
            for (BillingONFormViewModel.MultisiteProvider p : site.providers()) {
                String value = p.providerNo() + "|" + p.ohipNo();
                String label = p.lastName() + ", " + p.firstName();
                html.append("<option value='")
                        .append(escapeForHtmlAttr(value))
                        .append("'>")
                        .append(escapeForHtml(label))
                        .append("</option>");
            }
            siteHtml.put(site.name(), html.toString());
        }
        b.multisiteProviderHtml(siteHtml);

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
        for (int i = 0; i < io.github.carlos_emr.carlos.billings.ca.on.data.BillingDataHlp.FIELD_SERVICE_NUM; i++) {
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

        // ---- pre-rendered msg (errorMsg + warningMsg + DOB-invalid notice) ----
        StringBuilder msg = new StringBuilder("The default unit and @ value is 1.");
        msg.append(nullToEmpty(b.peekErrorMsg()));
        msg.append(nullToEmpty(b.peekWarningMsg()));
        if (b.peekDemoDobInvalid()) {
            msg.append("<br><b><font color='orange'>Warning: the patient's stored DOB is malformed; "
                    + "age-keyed premium codes and visit-type defaults are unreliable.</font></b><br>");
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
    private String resolveAssgProviderDisplay(HttpServletRequest request, String apptProviderNo) {
        if (apptProviderNo == null || apptProviderNo.isEmpty()) return "";
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
                MiscUtils.getLogger().warn(
                        "assgProvider display lookup failed for provider={}; rendering blank",
                        LogSanitizer.sanitize(apptProviderNo), e);
            }
        }
        if (name == null) return "";
        return name.length() > 15 ? name.substring(0, 14) : name;
    }

    /**
     * Loads the recent-billing history rows the JSP renders at the bottom of
     * the form. Up to 5 (claim, item) pairs from {@link JdbcBillingReviewImpl}.
     */
    private static List<BillingONFormViewModel.BillingHistoryRow> loadHistoryRows(String demoNo) {
        List<BillingONFormViewModel.BillingHistoryRow> rows = new ArrayList<>();
        if (demoNo == null || demoNo.isEmpty()) {
            return rows;
        }
        try {
            JdbcBillingReviewImpl reviewer = new JdbcBillingReviewImpl();
            List<Object> raw = reviewer.getBillingHist(demoNo, 5, 0, null);
            for (int i = 0; i + 1 < raw.size(); i += 2) {
                io.github.carlos_emr.carlos.billings.ca.on.data.BillingClaimHeader1Data header =
                        (io.github.carlos_emr.carlos.billings.ca.on.data.BillingClaimHeader1Data) raw.get(i);
                io.github.carlos_emr.carlos.billings.ca.on.data.BillingItemData item =
                        (io.github.carlos_emr.carlos.billings.ca.on.data.BillingItemData) raw.get(i + 1);
                String updateDt = nullToEmpty(header.getUpdate_datetime());
                rows.add(new BillingONFormViewModel.BillingHistoryRow(
                        nullToEmpty(header.getId()),
                        nullToEmpty(header.getBilling_date()),
                        nullToEmpty(item.getService_date()),
                        nullToEmpty(item.getService_code()),
                        nullToEmpty(item.getDx()),
                        updateDt.length() >= 10 ? updateDt.substring(0, 10) : updateDt));
            }
        } catch (RuntimeException rtEx) {
            MiscUtils.getLogger().error(
                    "Billing history rows lookup failed for demo={}; rendering with empty history",
                    LogSanitizer.sanitize(demoNo), rtEx);
        }
        return rows;
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
     * Inlined Drools billing-guidelines step (formerly the
     * {@code BillingONFormDroolsRecommender} composer — collapsed inline
     * because it was used by exactly one assembler and not reusable per
     * the package's scope contract). Catches broad {@code Exception} because
     * Drools rule compilation can throw {@code RuntimeException},
     * {@code KieBaseException}, or {@code OutOfMemoryError}-adjacent shapes;
     * the form must still render if the rule cache is corrupted.
     */
    private void recommendBillingGuidelines(BillingONFormViewModel.Builder b,
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
                    "Drools billing-guidelines evaluation failed for demo={} provider={}",
                    LogSanitizer.sanitize(demoNo), userNo, e);
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
