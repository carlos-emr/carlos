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
 * scriptlet block of {@code billingON.jsp} (lines 82-260 before this refactor).
 *
 * <p>Collaborates with {@link BillingONView2Action}, which constructs this
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

    private final DemographicManager demographicManager =
            SpringUtils.getBean(DemographicManager.class);
    private final ProfessionalSpecialistDao professionalSpecialistDao =
            SpringUtils.getBean(ProfessionalSpecialistDao.class);
    private final DxresearchDAO dxresearchDao =
            SpringUtils.getBean(DxresearchDAO.class);
    private final UserPropertyDAO userPropertyDao =
            SpringUtils.getBean(UserPropertyDAO.class);

    /**
     * Builds the view model for the main Ontario billing form. Matches the
     * scriptlet ordering in the original JSP so the resulting state is
     * equivalent to the pre-refactor page.
     */
    public BillingONFormViewModel assemble(LoggedInInfo loggedInInfo, HttpServletRequest request) {
        CarlosProperties oscarVars = CarlosProperties.getInstance();
        BillingONFormViewModel.Builder b = BillingONFormViewModel.builder();

        String userNo = (String) request.getSession().getAttribute("user");
        b.userNo(userNo);

        // xml_provider overrides providerview when present (matches original scriptlet behavior)
        String providerView = firstNonEmpty(
                request.getParameter("xml_provider"),
                firstNonEmpty(request.getParameter("providerview"), ""));
        if (providerView.indexOf('|') != -1) {
            providerView = providerView.substring(0, providerView.indexOf('|'));
        }
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
        b.clinicView(clinicView);
        b.clinicNo(clinicNo);
        b.visitType(visitType);

        String apptNo = request.getParameter("appointment_no");
        b.appointmentNo(apptNo);

        String billReferenceDate;
        if (apptNo != null && "0".equals(apptNo)) {
            billReferenceDate = firstNonNull(
                    request.getParameter("service_date"),
                    today);
        } else {
            billReferenceDate = request.getParameter("appointment_date");
        }
        b.billReferenceDate(billReferenceDate);

        String demoName = request.getParameter("demographic_name");
        String demoNo = request.getParameter("demographic_no");
        String apptProviderNo = request.getParameter("apptProvider_no");
        b.demoName(demoName);
        b.demographicNo(demoNo);
        b.apptProviderNo(apptProviderNo);

        String mReview = firstNonNull(request.getParameter("m_review"), "");
        b.mReview(mReview);
        b.ctlBillForm(request.getParameter("billForm"));
        b.curBillForm(request.getParameter("curBillForm"));

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
            demoLast = nullSafe(demo.getLastName());
            demoFirst = nullSafe(demo.getFirstName());
            demoDob = nullSafe(demo.getYearOfBirth())
                    + nullSafe(demo.getMonthOfBirth())
                    + nullSafe(demo.getDateOfBirth());
            demoHin = nullSafe(demo.getHin());
            demoVer = nullSafe(demo.getVer());
            demoHcType = nullSafe(demo.getHcType());
            demoSex = demo.getSex() != null && demo.getSex().startsWith("F") ? "2" : "1";
            familyDoctor = nullSafe(demo.getFamilyDoctor());
            assgProviderNo = nullSafe(demo.getProviderNo());
            rosterStatus = nullSafe(demo.getRosterStatus());
        }

        // HC type normalization: scriptlet coerced missing/short values to "ON"
        if (demoHcType == null || demoHcType.length() < 2) {
            demoHcType = "ON";
        } else {
            demoHcType = demoHcType.substring(0, 2).toUpperCase();
        }

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
                .age(calculateAge(demoDob));

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

        // Patient dx list (ICD-9 only) + add/match codes from user properties
        List<String> patientDx = new ArrayList<>();
        if (demoNo != null && !demoNo.isEmpty()) {
            try {
                List<Dxresearch> dxList = dxresearchDao.getByDemographicNo(Integer.parseInt(demoNo));
                for (Dxresearch dx : dxList) {
                    if ("icd9".equals(dx.getCodingSystem())) {
                        patientDx.add(dx.getDxresearchCode());
                    }
                }
            } catch (NumberFormatException nfe) {
                MiscUtils.getLogger().warn("Invalid demographic_no for dx lookup: {}", demoNo);
            }
        }
        b.patientDx(patientDx);

        UserProperty addCodeProp = userPropertyDao.getProp(UserProperty.CODE_TO_ADD_PATIENTDX);
        String patientDxAddCode = addCodeProp != null ? nullSafe(addCodeProp.getValue()).trim() : "";
        UserProperty matchCodeProp = userPropertyDao.getProp(UserProperty.CODE_TO_MATCH_PATIENTDX);
        String patientDxMatchCode = matchCodeProp != null ? nullSafe(matchCodeProp.getValue()).trim() : "";
        b.patientDxAddCode(patientDxAddCode)
                .patientDxMatchCode(patientDxMatchCode);

        // Billing guidelines (Drools) — yields warning-strength consequences as pre-rendered HTML
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
            MiscUtils.getLogger().error("Error evaluating billing guidelines", e);
        }
        b.billingRecommendations(recommendations.toString());

        // Billing history (first 5 entries) — the JSP only reads the first record's visitType / clinic_ref_code
        List<BillingONFormViewModel.BillingHistoryEntry> history = new ArrayList<>();
        if (demoNo != null && !demoNo.isEmpty()) {
            try {
                JdbcBillingReviewImpl reviewer = new JdbcBillingReviewImpl();
                List<Object> raw = reviewer.getBillingHist(demoNo, 5, 0, null);
                if (raw.size() >= 2) {
                    BillingClaimHeader1Data header = (BillingClaimHeader1Data) raw.get(0);
                    BillingItemData item = (BillingItemData) raw.get(1);
                    history.add(new BillingONFormViewModel.BillingHistoryEntry(
                            nullSafe(header.getAdmission_date()),
                            nullSafe(header.getVisittype()),
                            nullSafe(header.getFacilty_num()),
                            nullSafe(item.getDx())));
                }
            } catch (Exception e) {
                MiscUtils.getLogger().warn("Billing history lookup failed for demo={}", demoNo, e);
            }
        }
        b.billingHistory(history);

        // Provider list for the form's provider picker
        List<BillingONFormViewModel.ProviderOption> providers = new ArrayList<>();
        ProviderDao providerDao = SpringUtils.getBean(ProviderDao.class);
        for (Provider p : providerDao.getProvidersWithNonEmptyCredentials()) {
            providers.add(new BillingONFormViewModel.ProviderOption(
                    nullSafe(p.getLastName()),
                    nullSafe(p.getFirstName()),
                    nullSafe(p.getProviderNo()) + "|" + nullSafe(p.getOhipNo())));
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
                : (preference != null ? nullSafe(preference.getDefaultDxCode()) : "");
        if ((dxCode == null || dxCode.isEmpty()) && !history.isEmpty()) {
            dxCode = nullSafe(history.get(0).diagnosticCode());
        }
        b.dxCode(nullSafe(dxCode));

        // Default visit type: request param -> last-billed visit type -> existing visitType
        String xmlVisitTypeParam = request.getParameter("xml_visittype");
        String xmlVisitType = xmlVisitTypeParam != null && !xmlVisitTypeParam.isEmpty()
                ? xmlVisitTypeParam
                : (!history.isEmpty() ? nullSafe(history.get(0).visitType()) : "");
        if (!xmlVisitType.isEmpty()) {
            visitType = xmlVisitType;
        }
        b.visitType(nullSafe(visitType))
                .xmlVisitType(nullSafe(xmlVisitType));

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
            CtlBillingServiceDao ctlBillingServiceDao = SpringUtils.getBean(CtlBillingServiceDao.class);
            List<CtlBillingService> rosterBillSrvList = !rosterStatus.isEmpty()
                    ? ctlBillingServiceDao.findByServiceTypeId(rosterStatus)
                    : java.util.Collections.emptyList();

            if (!rosterBillSrvList.isEmpty() && !rosterStatus.isEmpty()) {
                ctlBillForm = rosterBillSrvList.get(0).getServiceType();
            } else {
                ProviderPreferenceDao providerPreferenceDao = SpringUtils.getBean(ProviderPreferenceDao.class);
                ProviderPreference providerPreference = (apptProviderNo != null && apptProviderNo.equalsIgnoreCase("none"))
                        ? providerPreferenceDao.find(userNo)
                        : providerPreferenceDao.find(apptProviderNo);

                if (providerPreference != null) {
                    defaultServiceType = nullSafe(providerPreference.getDefaultServiceType());
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
                    MyGroupDao myGroupDao = SpringUtils.getBean(MyGroupDao.class);
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
                .defaultServiceType(nullSafe(defaultServiceType));

        // xml_location -> clinicView; default "0000" if not set
        String xmlLocationParam = request.getParameter("xml_location");
        String xmlLocation = xmlLocationParam != null && !xmlLocationParam.isEmpty()
                ? xmlLocationParam
                : "0000";
        if (!xmlLocation.isEmpty()) {
            clinicView = xmlLocation;
        }
        String propertiesClinicView = CarlosProperties.getInstance().getProperty("clinic_view");
        if (propertiesClinicView != null) {
            clinicView = propertiesClinicView;
        }
        b.clinicView(nullSafe(clinicView))
                .xmlLocation(nullSafe(xmlLocation));

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

        CtlBillingServiceDao cbsDao = SpringUtils.getBean(CtlBillingServiceDao.class);
        BillingServiceDao bDao = SpringUtils.getBean(BillingServiceDao.class);
        CtlBillingServicePremiumDao pDao = SpringUtils.getBean(CtlBillingServicePremiumDao.class);
        CSSStylesDAO cssStylesDao = SpringUtils.getBean(CSSStylesDAO.class);
        CodeFilterManager codeFilterManager = SpringUtils.getBean(CodeFilterManager.class);

        java.util.Date filterDate = io.github.carlos_emr.carlos.util.ConversionUtils
                .fromDateString(billReferenceDate);
        if (request.getParameter("start_time") != null) {
            filterDate = io.github.carlos_emr.carlos.util.ConversionUtils
                    .fromTimestampString(billReferenceDate + " " + request.getParameter("start_time"));
        }
        java.util.Date billRefDate = io.github.carlos_emr.carlos.util.ConversionUtils
                .fromDateString(billReferenceDate);

        for (Object[] typeRow : cbsDao.findServiceTypesByStatus("A")) {
            String ctlcode = String.valueOf(typeRow[1]);
            String ctlcodename = String.valueOf(typeRow[0]);

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
                        CssStyle cssStyle = cssStylesDao.find(svc.getDisplayStyle());
                        if (cssStyle != null && cssStyle.getStyle() != null) {
                            displayStyle = cssStyle.getStyle();
                        }
                    }
                    groupEntries.add(new BillingONFormViewModel.ServiceCodeEntry(
                            nullSafe(svc.getServiceCode()),
                            svc.getDescription() == null ? "N/A" : svc.getDescription(),
                            nullSafe(svc.getValue()),
                            nullSafe(svc.getPercentage()),
                            nullSafe(ctl.getServiceType()),
                            nullSafe(ctl.getServiceGroupName()),
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
        CtlBillingTypeDao tDao = SpringUtils.getBean(CtlBillingTypeDao.class);
        String resolvedBillType = "";
        for (CtlBillingType t : tDao.findByServiceType(ctlBillForm)) {
            resolvedBillType = t.getBillType();
        }
        b.defaultBillType(nullSafe(resolvedBillType));

        // Billing-form menu: one entry per service type, with its billType (for Layer1 anchors + _billingForms JS array)
        List<BillingONFormViewModel.BillingFormMenuEntry> billingForms = new ArrayList<>();
        for (Object[] typeRow : cbsDao.findServiceTypesByStatus("A")) {
            String menuCode = String.valueOf(typeRow[1]);
            String menuName = String.valueOf(typeRow[0]);
            String menuBillType = "";
            for (CtlBillingType t : tDao.findByServiceType(menuCode)) {
                menuBillType = t.getBillType();
            }
            billingForms.add(new BillingONFormViewModel.BillingFormMenuEntry(
                    menuCode, menuName, nullSafe(menuBillType)));
        }
        b.billingForms(billingForms);

        // Dx codes grouped by service type (for Layer2 search panels)
        DiagnosticCodeDao dcDao = SpringUtils.getBean(DiagnosticCodeDao.class);
        java.util.LinkedHashMap<String, List<BillingONFormViewModel.DxCodeEntry>> dxByType =
                new java.util.LinkedHashMap<>();
        for (String st : serviceTypeCodes) {
            List<BillingONFormViewModel.DxCodeEntry> entries = new ArrayList<>();
            for (Object[] o : dcDao.findDiagnosictsAndCtlDiagCodesByServiceType(st)) {
                DiagnosticCode dx = (DiagnosticCode) o[0];
                entries.add(new BillingONFormViewModel.DxCodeEntry(
                        st,
                        nullSafe(dx.getDiagnosticCode()),
                        nullSafe(dx.getDescription())));
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

    private static int calculateAge(String dobYyyymmdd) {
        if (dobYyyymmdd == null || dobYyyymmdd.length() != 8) {
            return 0;
        }
        try {
            int year = Integer.parseInt(dobYyyymmdd.substring(0, 4));
            int month = Integer.parseInt(dobYyyymmdd.substring(4, 6));
            int day = Integer.parseInt(dobYyyymmdd.substring(6, 8));
            java.time.LocalDate dob = java.time.LocalDate.of(year, month, day);
            return java.time.Period.between(dob, java.time.LocalDate.now()).getYears();
        } catch (Exception e) {
            return 0;
        }
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }

    private static String firstNonNull(String primary, String fallback) {
        return primary != null ? primary : fallback;
    }

    private static String firstNonEmpty(String primary, String fallback) {
        return primary != null && !primary.isEmpty() ? primary : fallback;
    }
}
