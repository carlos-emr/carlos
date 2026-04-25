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
import io.github.carlos_emr.carlos.utility.LogSanitizer;
import io.github.carlos_emr.SxmlMisc;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.billing.CA.filters.CodeFilterManager;
import io.github.carlos_emr.carlos.billings.ca.bc.decisionSupport.BillingGuidelines;
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
import io.github.carlos_emr.carlos.commn.model.BillingService;
import io.github.carlos_emr.carlos.commn.model.CssStyle;
import io.github.carlos_emr.carlos.commn.model.CtlBillingService;
import io.github.carlos_emr.carlos.commn.model.CtlBillingServicePremium;
import io.github.carlos_emr.carlos.commn.model.CtlBillingType;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.DiagnosticCode;
import io.github.carlos_emr.carlos.commn.model.Dxresearch;
import io.github.carlos_emr.carlos.commn.model.MyGroup;
import io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.ProviderPreference;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.decisionSupport.model.DSConsequence;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SafeEncode;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.web.admin.ProviderPreferencesUIBean;

/**
 * Assembles the {@link BillingONFormViewModel} from request parameters and DAO
 * lookups. Extracts the data-preparation work that previously lived in the top
 * scriptlet block of {@code billingON.jsp} (the data-preparation scriptlet
 * preceding the rendered form, removed in this refactor).
 *
 * <p>Collaborates with {@link ViewBillingON2Action}, which constructs this
 * assembler, calls {@link #assemble(LoggedInInfo, HttpServletRequest)}, and
 * exposes the returned DTO to the JSP as request attribute {@code model}.</p>
 *
 * <p>Behavioral note: the scriptlet originally called
 * {@code response.sendRedirect("/logoutPage")} when {@code session.getAttribute("user")}
 * was null, but then continued executing (missing {@code return}). The view
 * action now short-circuits with a {@link SecurityException} before reaching
 * the assembler, so this class assumes {@code loggedInInfo} is non-null.</p>
 *
 * @since 2026-04-24
 */
public final class BillingONFormDataAssembler {

    /**
     * Matches a simple inline CSS string of the form
     * {@code property:value;property:value;...} where each property name and
     * value contains only safe characters. Used to gate DB-stored CSS values
     * before they're rendered straight into a {@code style="..."} attribute on
     * the service-code grid. Anything outside this shape is dropped.
     *
     * <p>Possessive quantifiers ({@code *+}, {@code ++}, {@code ?+}) prevent
     * catastrophic backtracking on adversarial inputs that interleave property
     * tokens (CodeQL findings 12885 / 12886). Each whitespace / value run
     * commits its match so the engine can never re-partition whitespace
     * between adjacent rules.</p>
     */
    private static final java.util.regex.Pattern SAFE_INLINE_STYLE = java.util.regex.Pattern.compile(
            "(?:[A-Za-z][A-Za-z0-9-]*+\\s*+:\\s*+[A-Za-z0-9 #_,.%/()\\-]++\\s*+;?+\\s*+)++");

    private final DemographicManager demographicManager;
    private final ProfessionalSpecialistDao professionalSpecialistDao;
    private final DxresearchDAO dxresearchDao;
    private final UserPropertyDAO userPropertyDao;
    private final ProviderDao providerDao;
    private final CtlBillingServiceDao ctlBillingServiceDao;
    private final ProviderPreferenceDao providerPreferenceDao;
    private final MyGroupDao myGroupDao;
    private final BillingServiceDao billingServiceDao;
    private final CtlBillingServicePremiumDao ctlBillingServicePremiumDao;
    private final CSSStylesDAO cssStylesDao;
    private final CodeFilterManager codeFilterManager;
    private final CtlBillingTypeDao ctlBillingTypeDao;
    private final DiagnosticCodeDao diagnosticCodeDao;

    /**
     * Production constructor used by Struts; resolves every dependency from
     * the Spring context via {@link SpringUtils#getBean}. Tests use the
     * package-private constructor below to inject mocks directly without
     * standing up a Spring context. Brings the assembler shape to parity
     * with {@code BillingShortcutPg1DataAssembler} and
     * {@code BillingONReviewDataAssembler}.
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
        this.demographicManager = demographicManager;
        this.professionalSpecialistDao = professionalSpecialistDao;
        this.dxresearchDao = dxresearchDao;
        this.userPropertyDao = userPropertyDao;
        this.providerDao = providerDao;
        this.ctlBillingServiceDao = ctlBillingServiceDao;
        this.providerPreferenceDao = providerPreferenceDao;
        this.myGroupDao = myGroupDao;
        this.billingServiceDao = billingServiceDao;
        this.ctlBillingServicePremiumDao = ctlBillingServicePremiumDao;
        this.cssStylesDao = cssStylesDao;
        this.codeFilterManager = codeFilterManager;
        this.ctlBillingTypeDao = ctlBillingTypeDao;
        this.diagnosticCodeDao = diagnosticCodeDao;
    }

    /**
     * Builds the view model for the main Ontario billing form. Matches the
     * scriptlet ordering in the original JSP so the resulting state is
     * equivalent to the pre-refactor page.
     */
    public BillingONFormViewModel assemble(LoggedInInfo loggedInInfo, HttpServletRequest request) {
        CarlosProperties oscarVars = CarlosProperties.getInstance();
        BillingONFormViewModel.Builder b = BillingONFormViewModel.builder();

        // Prefer the authenticated provider from LoggedInInfo over the session
        // attribute. The session "user" attribute is only set once (in
        // Login2Action#doProviderLogin — search for `setAttribute("user")`)
        // and never modified elsewhere, so the two should match. Pulling
        // from the authenticated context keeps the assembler consistent with
        // the action's identity check and avoids drift if the session
        // attribute is ever mutated by future code.
        String userNo = loggedInInfo != null ? loggedInInfo.getLoggedInProviderNo() : null;
        if (userNo == null || userNo.isEmpty()) {
            // Fall back to the session attribute for compatibility, but log so
            // the drift is visible.
            userNo = (String) request.getSession().getAttribute("user");
            if (userNo != null && !userNo.isEmpty()) {
                MiscUtils.getLogger().warn(
                        "BillingONFormDataAssembler: LoggedInInfo missing providerNo; "
                        + "falling back to session attribute \"user\"={}", userNo);
            }
        }
        b.userNo(userNo);

        // xml_provider ("providerNo|ohipNo" picker output) overrides
        // providerview when present. See BillingONRequestParams.extractProviderNo.
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
        // below after xml_location resolution (~line 495). The local
        // `clinicView` variable above stages the property/visit-type default
        // for that resolution step to layer the param override on top of.
        // Setting the builder twice would just discard the first write; a
        // future maintainer who removes "the duplicate" line below would
        // silently break the param override.
        b.clinicNo(clinicNo);
        b.visitType(visitType);

        // Missing appointment_no defaults to "0" — the legacy convention for
        // a manual bill not tied to an existing appointment. The JSP body
        // reads model.getAppointmentNo() and tests `appt_no.compareTo("0") == 0`
        // (search billingON.jsp for that comparison), so this default keeps
        // the EL-bridge code null-safe.
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

        // Coalesce request-param strings to empty rather than null. The JSP and
        // downstream JS string-builders call URLEncoder.encode(...) and concat
        // into href URLs, both of which NPE on null inputs.
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

        String providerNo = (apptProviderNo != null && apptProviderNo.equalsIgnoreCase("none"))
                ? userNo
                : apptProviderNo;
        b.providerNo(providerNo);

        Demographic demo = demographicManager.getDemographic(loggedInInfo, demoNo);

        String demoLast = "";
        String demoFirst = "";
        String demoHin = "";
        String demoVer = "";
        String demoDob = "";
        String demoHcType = "";
        String demoSex = "";
        String familyDoctor = "";
        String assgProviderNo = "";
        String rosterStatus = "";
        if (demo != null) {
            demoLast = nullToEmpty(demo.getLastName());
            demoFirst = nullToEmpty(demo.getFirstName());
            // Zero-pad month/day so the YYYYMMDD substring slices below land at
            // fixed positions. Without padding, a single-digit month (e.g. "4")
            // makes `demoDob` 6-7 chars, `substring(4, 6)` becomes "47", and
            // calculateAge() returns 0.
            demoDob = nullToEmpty(demo.getYearOfBirth())
                    + padTwo(nullToEmpty(demo.getMonthOfBirth()))
                    + padTwo(nullToEmpty(demo.getDateOfBirth()));
            demoHin = nullToEmpty(demo.getHin());
            demoVer = nullToEmpty(demo.getVer());
            demoHcType = nullToEmpty(demo.getHcType());
            demoSex = demo.getSex() != null && demo.getSex().startsWith("F") ? "2" : "1";
            familyDoctor = nullToEmpty(demo.getFamilyDoctor());
            assgProviderNo = nullToEmpty(demo.getProviderNo());
            rosterStatus = nullToEmpty(demo.getRosterStatus());
        }

        // HC type normalization: scriptlet coerced missing/short values to "ON"
        if (demoHcType == null || demoHcType.length() < 2) {
            demoHcType = "ON";
        } else {
            demoHcType = demoHcType.substring(0, 2).toUpperCase();
        }

        AgeResult ageResult = calculateAge(demoDob);
        b.demoLast(demoLast)
                .demoFirst(demoFirst)
                .demoHin(demoHin)
                .demoVer(demoVer)
                .demoDob(demoDob)
                .demoDobYear(demoDob.length() >= 4 ? demoDob.substring(0, 4) : "")
                .demoDobMonth(demoDob.length() >= 6 ? demoDob.substring(4, 6) : "")
                .demoDobDay(demoDob.length() >= 8 ? demoDob.substring(6, 8) : "")
                .demoHcType(demoHcType)
                .demoSex(demoSex)
                .familyDoctor(familyDoctor)
                .assgProviderNo(assgProviderNo)
                .rosterStatus(rosterStatus)
                .age(ageResult.age)
                .demoDobInvalid(ageResult.invalid);

        // Referral doctor is extracted from the family_doctor XML blob
        String rDoctor;
        String rDoctorOhip;
        String referralSpecialty = "";
        if (familyDoctor.isEmpty()) {
            rDoctor = "N/A";
            rDoctorOhip = "000000";
        } else {
            rDoctor = firstNonNull(SxmlMisc.getXmlContent(familyDoctor, "rd"), "");
            rDoctorOhip = firstNonNull(SxmlMisc.getXmlContent(familyDoctor, "rdohip"), "");
            ProfessionalSpecialist specialist = professionalSpecialistDao.getByReferralNo(rDoctorOhip);
            if (specialist != null) {
                rDoctor = specialist.getLastName() + "," + specialist.getFirstName();
                referralSpecialty = firstNonNull(specialist.getSpecialtyType(), "");
            }
        }
        b.referralDoctor(rDoctor)
                .referralDoctorOhip(rDoctorOhip)
                .referralSpecialty(referralSpecialty);

        // Warning / error validation messages — pre-formatted HTML, same as the scriptlet produced
        StringBuilder warning = new StringBuilder();
        StringBuilder error = new StringBuilder();
        String errorFlag = "";
        if (demoHin != null && demoHin.isEmpty()) {
            warning.append("<b><div class='alert alert-danger'>Warning: The patient does not have a valid HIN. </div></b>");
        }
        if (rDoctorOhip != null && !rDoctorOhip.isEmpty() && rDoctorOhip.length() != 6) {
            warning.append("<div class='alert alert error'>Warning: the referral doctor's no is wrong. </div>");
        }
        if (demoDob == null || demoDob.isEmpty() || demoDob.length() != 8) {
            errorFlag = "1";
            error.append("<b><div class='alert alert error'>Error: The patient does not have a valid DOB. </div></b>");
        }
        b.warningMsg(warning.toString())
                .errorMsg(error.toString())
                .errorFlag(errorFlag);

        b.patientDx(loadPatientDx(demoNo));

        UserProperty addCodeProp = userPropertyDao.getProp(UserProperty.CODE_TO_ADD_PATIENTDX);
        String patientDxAddCode = addCodeProp != null ? nullToEmpty(addCodeProp.getValue()).trim() : "";
        UserProperty matchCodeProp = userPropertyDao.getProp(UserProperty.CODE_TO_MATCH_PATIENTDX);
        String patientDxMatchCode = matchCodeProp != null ? nullToEmpty(matchCodeProp.getValue()).trim() : "";
        b.patientDxAddCode(patientDxAddCode)
                .patientDxMatchCode(patientDxMatchCode);

        // Billing guidelines (Drools) — yields warning-strength consequences as pre-rendered HTML.
        // Include demoNo/userNo in the log: BillingGuidelines.getInstance()
        // lazy-compiles DRL via RuleBaseFactory, so a corrupt rule cache fails
        // identically for every patient until cleared. Without this context,
        // ops can't distinguish a one-off failure from a global outage.
        StringBuilder recommendations = new StringBuilder();
        try {
            List<DSConsequence> consequences = BillingGuidelines.getInstance()
                    .evaluateAndGetConsequences(loggedInInfo, demoNo, userNo);
            for (DSConsequence dscon : consequences) {
                if (dscon.getConsequenceStrength() == DSConsequence.ConsequenceStrength.warning) {
                    recommendations.append(SafeEncode.forHtml(dscon.getText())).append("<br/>");
                }
            }
        } catch (Exception e) {
            MiscUtils.getLogger().error(
                    "Drools billing-guidelines evaluation failed for demo={} provider={}",
                    demoNo, userNo, e);
        }
        b.billingRecommendations(recommendations.toString());

        // Capture history into a local — downstream blocks (default dx code,
        // default visit type) read from it.
        List<BillingONFormViewModel.BillingHistoryEntry> history = loadBillingHistory(demoNo);
        b.billingHistory(history);

        // Provider list for the form's provider picker
        List<BillingONFormViewModel.ProviderOption> providers = new ArrayList<>();
        for (Provider p : providerDao.getProvidersWithNonEmptyCredentials()) {
            providers.add(new BillingONFormViewModel.ProviderOption(
                    nullToEmpty(p.getLastName()),
                    nullToEmpty(p.getFirstName()),
                    nullToEmpty(p.getProviderNo()) + "|" + nullToEmpty(p.getOhipNo())));
        }
        b.providers(providers);

        // Provider preference (used for dx-code and service-type defaults)
        ProviderPreference preference = (providerNo != null && !providerNo.isEmpty())
                ? ProviderPreferencesUIBean.getProviderPreferenceByProviderNo(providerNo)
                : null;

        // Default dx code: request param -> provider preference -> last-billed dx
        String dxCodeParam = request.getParameter("dxCode");
        String dxCode = dxCodeParam != null && !dxCodeParam.isEmpty()
                ? dxCodeParam
                : (preference != null ? nullToEmpty(preference.getDefaultDxCode()) : "");
        if ((dxCode == null || dxCode.isEmpty()) && !history.isEmpty()) {
            dxCode = nullToEmpty(history.get(0).diagnosticCode());
        }
        b.dxCode(nullToEmpty(dxCode));

        // Default visit type: request param -> last-billed visit type -> existing visitType
        String xmlVisitTypeParam = request.getParameter("xml_visittype");
        String xmlVisitType = xmlVisitTypeParam != null && !xmlVisitTypeParam.isEmpty()
                ? xmlVisitTypeParam
                : (!history.isEmpty() ? nullToEmpty(history.get(0).visitType()) : "");
        if (!xmlVisitType.isEmpty()) {
            visitType = xmlVisitType;
        }
        b.visitType(nullToEmpty(visitType))
                .xmlVisitType(nullToEmpty(xmlVisitType));

        // Billing form (ctlBillForm) resolution — priority order:
        // 1. curBillForm request param (user's explicit pick)
        // 2. roster-status-specific billing service
        // 3. provider preference
        // 4. group default billing form
        // 5. carlos.properties default_view
        String curBillForm = request.getParameter("curBillForm");
        String ctlBillForm = request.getParameter("billForm");
        String defaultServiceType = "";

        if (curBillForm != null) {
            ctlBillForm = curBillForm;
        } else {
            List<CtlBillingService> rosterBillSrvList = !rosterStatus.isEmpty()
                    ? ctlBillingServiceDao.findByServiceTypeId(rosterStatus)
                    : java.util.Collections.emptyList();

            if (!rosterBillSrvList.isEmpty() && !rosterStatus.isEmpty()) {
                ctlBillForm = rosterBillSrvList.get(0).getServiceType();
            } else {
                ProviderPreference providerPreference = (apptProviderNo != null && apptProviderNo.equalsIgnoreCase("none"))
                        ? providerPreferenceDao.find(userNo)
                        : providerPreferenceDao.find(apptProviderNo);

                if (providerPreference != null) {
                    defaultServiceType = nullToEmpty(providerPreference.getDefaultServiceType());
                }

                if (("QU - Quebec".equals(rosterStatus) || "FS".equals(rosterStatus))
                        && !"RN".equals(defaultServiceType)) {
                    defaultServiceType = "PRI";
                }
                if (defaultServiceType != null
                        && !defaultServiceType.isEmpty()
                        && !"no".equals(defaultServiceType)
                        && providerPreference != null) {
                    ctlBillForm = providerPreference.getDefaultServiceType();
                } else {
                    List<MyGroup> myGroups = myGroupDao.getProviderGroups(providerNo);
                    for (MyGroup group : myGroups) {
                        String groupBillForm = group.getDefaultBillingForm();
                        if (groupBillForm != null && !groupBillForm.isEmpty()) {
                            ctlBillForm = groupBillForm;
                            break;
                        }
                    }
                    if (ctlBillForm == null || ctlBillForm.isEmpty()) {
                        String dv = CarlosProperties.getInstance().getProperty("default_view");
                        if (dv != null) {
                            ctlBillForm = dv;
                        }
                    }
                }
            }
        }

        if (ctlBillForm == null) {
            ctlBillForm = "";
        }

        // Post-selection overrides: MIP / PRI based on visit type + roster
        if ((visitType.startsWith("02") || visitType.startsWith("04"))
                && !"RN".equals(defaultServiceType)) {
            ctlBillForm = "MIP";
        }
        if (("QU - Quebec".equals(rosterStatus) || "FS".equals(rosterStatus))
                && !"RN".equals(defaultServiceType)) {
            ctlBillForm = "PRI";
        }

        b.ctlBillForm(ctlBillForm)
                .defaultServiceType(nullToEmpty(defaultServiceType));

        // Resolution order (matches legacy scriptlet intent):
        //   1. user-selected xml_location query param wins (including "0000",
        //      which is a legitimate "no-location" selection in some installs);
        //   2. CarlosProperties.clinic_view is the per-installation default;
        //   3. "0000" is the final fallback if both are missing.
        // Earlier code dropped a param value of "0000" and silently fell
        // through to the property — that violated the user's explicit pick.
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

        // xml_vdate -> visitDate; empty if not explicitly supplied
        String xmlVdateParam = request.getParameter("xml_vdate");
        String visitDate = xmlVdateParam != null ? xmlVdateParam : "";
        b.visitDate(visitDate);

        // Service-code grid: 3 groups x N service types, plus titles + premium flags
        java.util.LinkedHashMap<String, List<BillingONFormViewModel.ServiceCodeEntry>> serviceCodesMap
                = new java.util.LinkedHashMap<>();
        List<String> serviceTypeCodes = new ArrayList<>();
        java.util.LinkedHashMap<String, String> serviceTitleMap = new java.util.LinkedHashMap<>();
        java.util.LinkedHashSet<String> premiumCodes = new java.util.LinkedHashSet<>();
        String resolvedBillFormName = "";

        // Field aliases (cbsDao / bDao / pDao) preserved so the surrounding
        // service-code grid logic — long enough to be its own helper — keeps
        // its existing variable names without shadow renames in every spot.
        CtlBillingServiceDao cbsDao = ctlBillingServiceDao;
        BillingServiceDao bDao = billingServiceDao;
        CtlBillingServicePremiumDao pDao = ctlBillingServicePremiumDao;

        java.util.Date filterDate = io.github.carlos_emr.carlos.util.ConversionUtils
                .fromDateString(billReferenceDate);
        if (request.getParameter("start_time") != null) {
            filterDate = io.github.carlos_emr.carlos.util.ConversionUtils
                    .fromTimestampString(billReferenceDate + " " + request.getParameter("start_time"));
        }
        java.util.Date billRefDate = io.github.carlos_emr.carlos.util.ConversionUtils
                .fromDateString(billReferenceDate);

        // One DAO roundtrip for the service-type rows; reused below for the
        // billing-form menu so we don't issue an identical findServiceTypesByStatus
        // a second time during the same render.
        @SuppressWarnings("unchecked")
        List<Object[]> serviceTypeRows = (List<Object[]>) cbsDao.findServiceTypesByStatus("A");
        for (Object[] typeRow : serviceTypeRows) {
            // Skip rows where the code column is null — `String.valueOf((Object)null)`
            // returns the literal 4-character string "null", which would render
            // `id="null"` in the DOM and `billForm=null` in click-through URLs.
            // ctl_billservice.servicetype is conventionally non-null, but a
            // left-join or schema-drift could produce one; log + continue.
            if (typeRow == null || typeRow[1] == null) {
                MiscUtils.getLogger().warn(
                        "ctl_billservice service-type row has null code column; skipping");
                continue;
            }
            // Sanitize the code at ingest so the same string flows through
            // every downstream surface (HTML element ids, EL ${st}, scriptlet
            // <%=st%>, JS args). Without this, a malformed DB row containing
            // whitespace could emit invalid HTML (id="..."  contains a space)
            // and the JS lookup-by-id would fail intermittently across browsers.
            String ctlcode = sanitizeIdToken(String.valueOf(typeRow[1]));
            String ctlcodename = typeRow[0] == null ? "" : String.valueOf(typeRow[0]);

            if (ctlcode.equals(ctlBillForm)) {
                resolvedBillFormName = ctlcodename;
            }
            serviceTypeCodes.add(ctlcode);

            for (String groupName : new String[] { "Group1", "Group2", "Group3" }) {
                List<BillingONFormViewModel.ServiceCodeEntry> groupEntries = new ArrayList<>();
                for (Object[] o : bDao.findBillingServiceAndCtlBillingServiceByMagic(ctlcode, groupName, billRefDate)) {
                    BillingService svc = (BillingService) o[0];
                    CtlBillingService ctl = (CtlBillingService) o[1];
                    if (!codeFilterManager.isCodeValid(svc.getServiceCode(), null, false, filterDate, demo)) {
                        continue;
                    }
                    String displayStyle = "";
                    if (svc.getDisplayStyle() != null) {
                        // TODO(perf): one DB roundtrip per service code (~240
                        // for an 8-service-type x 3-group install). Pre-existing
                        // scriptlet pattern carried across the migration; a
                        // future refactor should batch via cssStylesDao.findAll()
                        // into a HashMap<String, CssStyle> outside the loop.
                        CssStyle cssStyle = cssStylesDao.find(svc.getDisplayStyle());
                        if (cssStyle != null && cssStyle.getStyle() != null) {
                            // The displayStyle string is rendered straight into the
                            // service-grid <td style="..."> attribute. Allow only
                            // the simple "property:value;property:value;" shape so
                            // a malformed DB row can't break out of the attribute
                            // (CSS injection). Anything that doesn't match the
                            // whitelist is dropped. A future improvement is mapping
                            // displayStyle keys to a stable CSS class instead of
                            // emitting raw values.
                            if (SAFE_INLINE_STYLE.matcher(cssStyle.getStyle()).matches()) {
                                displayStyle = cssStyle.getStyle();
                            } else {
                                MiscUtils.getLogger().warn(
                                        "Dropped malformed inline CSS for service code {}: {}",
                                        svc.getServiceCode(), cssStyle.getStyle());
                            }
                        }
                    }
                    groupEntries.add(new BillingONFormViewModel.ServiceCodeEntry(
                            nullToEmpty(svc.getServiceCode()),
                            svc.getDescription() == null ? "N/A" : svc.getDescription(),
                            nullToEmpty(svc.getValue()),
                            nullToEmpty(svc.getPercentage()),
                            nullToEmpty(ctl.getServiceType()),
                            nullToEmpty(ctl.getServiceGroupName()),
                            displayStyle,
                            svc.getSliFlag()));
                    serviceTitleMap.put(
                            groupName.toLowerCase().replace("group", "group") + "_" + ctlcode,
                            ctl.getServiceGroupName());
                }
                if (!groupEntries.isEmpty()) {
                    List<String> codes = new ArrayList<>();
                    for (BillingONFormViewModel.ServiceCodeEntry e : groupEntries) {
                        codes.add(e.serviceCode());
                    }
                    for (CtlBillingServicePremium p : pDao.findByServceCodes(codes)) {
                        premiumCodes.add(p.getServiceCode());
                    }
                }
                String mapKey = groupName.toLowerCase() + "_" + ctlcode;
                serviceCodesMap.put(mapKey, groupEntries);
            }
        }

        b.billingServiceCodesMap(serviceCodesMap)
                .listServiceType(serviceTypeCodes)
                .titleMap(serviceTitleMap)
                .premiumCodes(premiumCodes)
                .defaultBillFormName(resolvedBillFormName);

        // Default bill type for the selected service type
        CtlBillingTypeDao tDao = ctlBillingTypeDao;
        String resolvedBillType = "";
        for (CtlBillingType t : tDao.findByServiceType(ctlBillForm)) {
            resolvedBillType = t.getBillType();
        }
        b.defaultBillType(nullToEmpty(resolvedBillType));

        // Billing-form menu: one entry per service type, with its billType
        // (for Layer1 anchors + _billingForms JS array). Reuses the
        // serviceTypeRows captured above instead of re-querying the DAO.
        // Sanitize codes via sanitizeIdToken consistently with the grid loop
        // above so a malformed DB row produces the same id token in both the
        // grid div and the menu URL — otherwise the click-to-show round-trip
        // would lookup a different id than the rendered one.
        //
        // TODO(perf): the inner tDao.findByServiceType(menuCode) call is N+1
        // (~8 DAO roundtrips per render for a typical install). The DAO does
        // not currently expose a batch findByServiceTypes(Collection<String>)
        // — adding one + caching the result here would replace the N+1 with
        // 1 query per render. Out of scope for the current refactor (no
        // schema-touching changes) but worth a follow-up issue.
        List<BillingONFormViewModel.BillingFormMenuEntry> billingForms = new ArrayList<>();
        for (Object[] typeRow : serviceTypeRows) {
            if (typeRow == null || typeRow[1] == null) {
                continue; // already logged in the grid loop above
            }
            String menuCode = sanitizeIdToken(String.valueOf(typeRow[1]));
            String menuName = typeRow[0] == null ? "" : String.valueOf(typeRow[0]);
            String menuBillType = "";
            for (CtlBillingType t : tDao.findByServiceType(menuCode)) {
                menuBillType = t.getBillType();
            }
            billingForms.add(new BillingONFormViewModel.BillingFormMenuEntry(
                    menuCode, menuName, nullToEmpty(menuBillType)));
        }
        b.billingForms(billingForms);

        // Dx codes grouped by service type (for Layer2 search panels)
        DiagnosticCodeDao dcDao = diagnosticCodeDao;
        java.util.LinkedHashMap<String, List<BillingONFormViewModel.DxCodeEntry>> dxByType =
                new java.util.LinkedHashMap<>();
        for (String st : serviceTypeCodes) {
            List<BillingONFormViewModel.DxCodeEntry> entries = new ArrayList<>();
            for (Object[] o : dcDao.findDiagnosictsAndCtlDiagCodesByServiceType(st)) {
                DiagnosticCode dx = (DiagnosticCode) o[0];
                entries.add(new BillingONFormViewModel.DxCodeEntry(
                        st,
                        nullToEmpty(dx.getDiagnosticCode()),
                        nullToEmpty(dx.getDescription())));
            }
            dxByType.put(st, entries);
        }
        b.dxCodesByServiceType(dxByType);

        // Billing favourites (flat name/code list for the cutlist dropdown)
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
     */
    static final class AgeResult {
        final int age;
        final boolean invalid;
        AgeResult(int age, boolean invalid) { this.age = age; this.invalid = invalid; }
    }

    static AgeResult calculateAge(String dobYyyymmdd) {
        if (dobYyyymmdd == null || dobYyyymmdd.isEmpty()) {
            // Empty DOB is the "no patient yet" case, not a parse failure.
            return new AgeResult(0, false);
        }
        if (dobYyyymmdd.length() != 8) {
            MiscUtils.getLogger().warn(
                    "calculateAge: DOB '{}' is not 8 chars; flagging invalid",
                    LogSanitizer.sanitize(dobYyyymmdd));
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
                    "calculateAge: malformed DOB '{}'; flagging invalid",
                    LogSanitizer.sanitize(dobYyyymmdd), e);
            return new AgeResult(0, true);
        }
    }

    /**
     * Loads the patient's ICD-9 diagnosis list. Pure read; no side effects.
     * Extracted from {@code assemble()} for testability and to keep the main
     * method's flow legible.
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
     * RuntimeException is WARN (DB outage — billing form still safe to
     * render with empty history).
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
                    demoNo, ccEx);
        } catch (RuntimeException rtEx) {
            // Promoted from WARN to ERROR: a billing-only DB outage that
            // strips the visit-context hint from the form is high-impact
            // for clinical workflow (provider may duplicate-bill the same
            // encounter). On-call should see this at the same severity as
            // the data-shape regression above.
            MiscUtils.getLogger().error(
                    "Billing history lookup failed for demo={}; rendering with empty history",
                    demoNo, rtEx);
        }
        return history;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /**
     * Reduces a DB-supplied service-type / bill-form code to a safe HTML id
     * token. Replaces every character outside {@code [A-Za-z0-9_-]} with
     * {@code _}. Service-type codes are conventionally short alphanumerics in
     * the {@code ctl_billservice} table, so this is a no-op in practice; it
     * exists to keep the rendered DOM ids well-formed if a malformed row ever
     * makes it into the table. Package-private to allow direct unit-testing
     * of the regex behavior — this is the gate against future changes to the
     * safe-character set silently breaking DOM-id integrity across browsers.
     */
    static String sanitizeIdToken(String s) {
        if (s == null) {
            return "";
        }
        return s.replaceAll("[^A-Za-z0-9_-]", "_");
    }

    private static String padTwo(String value) {
        return value != null && value.length() == 1 ? "0" + value : nullToEmpty(value);
    }

    private static String firstNonNull(String primary, String fallback) {
        return primary != null ? primary : fallback;
    }
}
