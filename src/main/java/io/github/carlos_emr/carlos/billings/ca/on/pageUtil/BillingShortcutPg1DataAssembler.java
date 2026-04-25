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
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.Misc;
import io.github.carlos_emr.SxmlMisc;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.billing.CA.dao.BillingDetailDao;
import io.github.carlos_emr.carlos.billing.CA.model.BillingDetail;
import io.github.carlos_emr.carlos.billings.ca.on.data.BillingClaimHeader1Data;
import io.github.carlos_emr.carlos.billings.ca.on.data.BillingItemData;
import io.github.carlos_emr.carlos.billings.ca.on.data.BillingShortcutPg1ViewModel;
import io.github.carlos_emr.carlos.billings.ca.on.data.JdbcBillingReviewImpl;
import io.github.carlos_emr.carlos.commn.dao.BillingDao;
import io.github.carlos_emr.carlos.commn.dao.BillingServiceDao;
import io.github.carlos_emr.carlos.commn.dao.ClinicLocationDao;
import io.github.carlos_emr.carlos.commn.dao.CtlBillingServicePremiumDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.ProfessionalSpecialistDao;
import io.github.carlos_emr.carlos.commn.model.Billing;
import io.github.carlos_emr.carlos.commn.model.BillingService;
import io.github.carlos_emr.carlos.commn.model.ClinicLocation;
import io.github.carlos_emr.carlos.commn.model.CtlBillingService;
import io.github.carlos_emr.carlos.commn.model.CtlBillingServicePremium;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.util.ConversionUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Assembles {@link BillingShortcutPg1ViewModel} for {@code billingShortcutPg1.jsp}.
 *
 * <p>Encapsulates the 14 DAO lookups, billing-history walk, service-code grid
 * prep, and demographic-driven validation messaging that previously lived in
 * the 350-line top scriptlet of the legacy JSP. The shortcut page is the
 * fast-track hospital-billing variant of {@code billingON.jsp}, so the prep
 * shape mirrors {@link BillingONFormDataAssembler} where it can — both pull
 * provider lists, location lists, billing-service grids — with shortcut-only
 * differences (hospital-billing visit-type override, legacy
 * {@code BillingDao.findActiveBillingsByDemoNo} history walk).</p>
 *
 * @since 2026-04-24
 */
public final class BillingShortcutPg1DataAssembler {

    private final DemographicDao demographicDao;
    private final ProviderDao providerDao;
    private final BillingDao billingDao;
    private final BillingDetailDao billingDetailDao;
    private final BillingServiceDao billingServiceDao;
    private final CtlBillingServicePremiumDao ctlBillingServicePremiumDao;
    private final ClinicLocationDao clinicLocationDao;
    private final ProfessionalSpecialistDao professionalSpecialistDao;
    private final java.util.function.Supplier<JdbcBillingReviewImpl> billingReviewImplFactory;

    public BillingShortcutPg1DataAssembler() {
        this(SpringUtils.getBean(DemographicDao.class),
             SpringUtils.getBean(ProviderDao.class),
             SpringUtils.getBean(BillingDao.class),
             SpringUtils.getBean(BillingDetailDao.class),
             SpringUtils.getBean(BillingServiceDao.class),
             SpringUtils.getBean(CtlBillingServicePremiumDao.class),
             SpringUtils.getBean(ClinicLocationDao.class),
             SpringUtils.getBean(ProfessionalSpecialistDao.class),
             JdbcBillingReviewImpl::new);
    }

    BillingShortcutPg1DataAssembler(DemographicDao demographicDao,
                                    ProviderDao providerDao,
                                    BillingDao billingDao,
                                    BillingDetailDao billingDetailDao,
                                    BillingServiceDao billingServiceDao,
                                    CtlBillingServicePremiumDao ctlBillingServicePremiumDao,
                                    ClinicLocationDao clinicLocationDao,
                                    ProfessionalSpecialistDao professionalSpecialistDao) {
        this(demographicDao, providerDao, billingDao, billingDetailDao,
             billingServiceDao, ctlBillingServicePremiumDao,
             clinicLocationDao, professionalSpecialistDao,
             JdbcBillingReviewImpl::new);
    }

    BillingShortcutPg1DataAssembler(DemographicDao demographicDao,
                                    ProviderDao providerDao,
                                    BillingDao billingDao,
                                    BillingDetailDao billingDetailDao,
                                    BillingServiceDao billingServiceDao,
                                    CtlBillingServicePremiumDao ctlBillingServicePremiumDao,
                                    ClinicLocationDao clinicLocationDao,
                                    ProfessionalSpecialistDao professionalSpecialistDao,
                                    java.util.function.Supplier<JdbcBillingReviewImpl> billingReviewImplFactory) {
        this.demographicDao = demographicDao;
        this.providerDao = providerDao;
        this.billingDao = billingDao;
        this.billingDetailDao = billingDetailDao;
        this.billingServiceDao = billingServiceDao;
        this.ctlBillingServicePremiumDao = ctlBillingServicePremiumDao;
        this.clinicLocationDao = clinicLocationDao;
        this.professionalSpecialistDao = professionalSpecialistDao;
        this.billingReviewImplFactory = billingReviewImplFactory;
    }

    public BillingShortcutPg1ViewModel assemble(HttpServletRequest request, String userProviderNo) {
        CarlosProperties props = CarlosProperties.getInstance();
        boolean hospitalBilling = true;

        String clinicView = hospitalBilling
                ? nullToEmpty(props.getProperty("clinic_hospital", ""))
                : nullToEmpty(props.getProperty("clinic_view", ""));
        String clinicNo = nullToEmpty(props.getProperty("clinic_no", ""));
        String visitType = hospitalBilling ? "02" : nullToEmpty(props.getProperty("visit_type", ""));

        String apptNo = nullToEmpty(request.getParameter("appointment_no"));
        String demoName = nullToEmpty(request.getParameter("demographic_name"));
        String demoNo = nullToEmpty(request.getParameter("demographic_no"));
        String apptProviderNo = nullToEmpty(request.getParameter("apptProvider_no"));
        String apptDate = nullToEmpty(request.getParameter("appointment_date"));
        String startTime = nullToEmpty(request.getParameter("start_time"));
        String ctlBillForm = nullToEmpty(request.getParameter("billForm"));
        String assignedProviderNo = nullToEmpty(request.getParameter("assgProvider_no"));

        // providerview default order: xml_provider param > providerview param >
        // logged-in provider. The legacy View2Action defaulted to userProviderNo
        // when both params were absent; preserve that.
        // xml_provider is "providerNo|ohipNo" from the picker — strip the suffix
        // and don't let an empty value clobber a populated providerview.
        String providerView = BillingONRequestParams.extractProviderNo(
                request.getParameter("xml_provider"),
                request.getParameter("providerview"));
        if (providerView.isEmpty()) {
            providerView = nullToEmpty(userProviderNo);
        }

        DemographicLoad demoLoad = loadDemographic(demoNo, request.getParameter("DemoSex"));
        if (!demoLoad.assignedProviderOverride.isEmpty()) {
            assignedProviderNo = demoLoad.assignedProviderOverride;
        }

        // Billing history (5 most recent)
        List<Properties> billingHistory = new ArrayList<>();
        List<Properties> billingHistoryDetails = new ArrayList<>();
        String referralDoctorOhip = demoLoad.referralDoctorOhip;
        boolean isNewOnBilling = "true".equals(props.getProperty("isNewONbilling", ""));

        // Per-row try/catch so a single malformed billing row doesn't wipe
        // the whole 5-bill history. Outer catch protects the DAO call itself
        // (DB outage); each row catch protects against per-row data-shape
        // regression (ClassCastException) and per-row corruption.
        try {
            if (!isNewOnBilling) {
                boolean firstReferral = true;
                Integer demoIdInt = ConversionUtils.fromIntString(demoNo);
                for (Billing b : billingDao.findActiveBillingsByDemoNo(demoIdInt, 5)) {
                    // Capture bill id outside the try so the catch handler
                    // never re-dereferences `b` (which would NPE if the row
                    // itself was null and abort recovery for the rest of the
                    // loop).
                    Object capturedBillId = b == null ? "<null>" : b.getId();
                    try {
                        Properties p = new Properties();
                        p.setProperty("billing_no", "" + b.getId());
                        p.setProperty("visitdate", ConversionUtils.toDateString(b.getVisitDate()));
                        p.setProperty("billing_date", ConversionUtils.toDateString(b.getBillingDate()));
                        p.setProperty("update_date", ConversionUtils.toDateString(b.getUpdateDate()));
                        p.setProperty("visitType", nullToEmpty(b.getVisitType()));
                        p.setProperty("clinic_ref_code", nullToEmpty(b.getClinicRefCode()));
                        billingHistory.add(p);

                        if (firstReferral && "checked".equals(SxmlMisc.getXmlContent(b.getContent(), "xml_referral"))) {
                            firstReferral = false;
                            String rdohip = SxmlMisc.getXmlContent(b.getContent(), "rdohip");
                            if (rdohip != null) {
                                referralDoctorOhip = rdohip;
                            }
                        }
                    } catch (RuntimeException rowEx) {
                        io.github.carlos_emr.carlos.utility.MiscUtils.getLogger().warn(
                                "Shortcut history: skipping malformed billing row id={} for demo={}",
                                capturedBillId, demoNo, rowEx);
                    }
                }

                for (Properties hist : billingHistory) {
                    String billingNo = hist.getProperty("billing_no", "");
                    // Build the comma-joined dx and service-code summaries for one
                    // bill, deduplicating against the FULL accumulated set rather
                    // than just the last entry. The legacy JSP did `last.equals(...)`
                    // which broke as soon as any non-adjacent code matched a prior
                    // one — for instance "401, 402, 401" would emit "401, 402, 401"
                    // even though "401" already appeared.
                    java.util.LinkedHashSet<String> dxSeen = new java.util.LinkedHashSet<>();
                    java.util.LinkedHashSet<String> serSeen = new java.util.LinkedHashSet<>();
                    try {
                        for (BillingDetail bd : billingDetailDao.findByBillingNo(ConversionUtils.fromIntString(billingNo))) {
                            if (bd.getDiagnosticCode() != null && !bd.getDiagnosticCode().isEmpty()) {
                                dxSeen.add(bd.getDiagnosticCode());
                            }
                            if (bd.getServiceCode() != null && !bd.getServiceCode().isEmpty()) {
                                serSeen.add(bd.getServiceCode() + " x " + bd.getBillingUnit());
                            }
                        }
                    } catch (RuntimeException detailEx) {
                        io.github.carlos_emr.carlos.utility.MiscUtils.getLogger().warn(
                                "Shortcut history: detail lookup failed for billing_no={}",
                                billingNo, detailEx);
                    }
                    Properties detail = new Properties();
                    detail.setProperty("service_code", String.join(", ", serSeen));
                    detail.setProperty("diagnostic_code", String.join(", ", dxSeen));
                    billingHistoryDetails.add(detail);
                }
            } else {
                JdbcBillingReviewImpl hdbObj = billingReviewImplFactory.get();
                List<?> aL = hdbObj.getBillingHist(demoNo, 5, 0, null);
                // aL contains alternating pairs of (BillingClaimHeader1Data, BillingItemData).
                // Walk pairwise; per-pair try/catch so a single bad pair doesn't
                // strip the surrounding good rows.
                for (int i = 0; i + 1 < aL.size(); i += 2) {
                    try {
                        BillingClaimHeader1Data obj = (BillingClaimHeader1Data) aL.get(i);
                        BillingItemData iobj = (BillingItemData) aL.get(i + 1);
                        // Build BOTH Properties before adding either to the
                        // parent lists. If detail construction throws, neither
                        // list is mutated — keeps billingHistory and
                        // billingHistoryDetails strictly in sync at index
                        // boundaries so the JSP iteration can pair them
                        // positionally.
                        Properties p = new Properties();
                        p.setProperty("billing_no", nullToEmpty(String.valueOf(obj.getId())));
                        p.setProperty("billing_date", nullToEmpty(obj.getBilling_date()));
                        p.setProperty("visitdate", obj.getAdmission_date() == null ? "" : obj.getAdmission_date());
                        p.setProperty("visitType", nullToEmpty(obj.getVisittype()));
                        p.setProperty("clinic_ref_code", nullToEmpty(obj.getFacilty_num()));
                        String updateDt = obj.getUpdate_datetime();
                        p.setProperty("update_date", updateDt != null && updateDt.length() >= 10 ? updateDt.substring(0, 10) : nullToEmpty(updateDt));

                        Properties detail = new Properties();
                        detail.setProperty("service_code", nullToEmpty(iobj.getService_code()));
                        detail.setProperty("diagnostic_code", nullToEmpty(iobj.getDx()));

                        billingHistory.add(p);
                        billingHistoryDetails.add(detail);
                    } catch (ClassCastException ccEx) {
                        io.github.carlos_emr.carlos.utility.MiscUtils.getLogger().error(
                                "Shortcut history: data-shape regression at pair index {} for demo={}",
                                i, demoNo, ccEx);
                    } catch (RuntimeException rowEx) {
                        io.github.carlos_emr.carlos.utility.MiscUtils.getLogger().warn(
                                "Shortcut history: skipping malformed pair at index {} for demo={}",
                                i, demoNo, rowEx);
                    }
                }
            }
        } catch (RuntimeException rtEx) {
            // Outer DAO call failed (DB outage). Render with empty history.
            // Logged at ERROR (matching BillingONFormDataAssembler) because a
            // missing history surface in the shortcut workflow has the same
            // duplicate-bill risk as the main form: provider can't see what
            // was already billed for this demographic.
            io.github.carlos_emr.carlos.utility.MiscUtils.getLogger().error(
                    "Shortcut billing history lookup failed for demo={}; rendering with empty history",
                    demoNo, rtEx);
            billingHistory.clear();
            billingHistoryDetails.clear();
        }

        // Provider list (doctors with OHIP)
        List<Properties> providers = new ArrayList<>();
        for (Provider pr : providerDao.getDoctorsWithOhip()) {
            Properties p = new Properties();
            p.setProperty("last_name", nullToEmpty(pr.getLastName()));
            p.setProperty("first_name", nullToEmpty(pr.getFirstName()));
            p.setProperty("proOHIP", nullToEmpty(pr.getProviderNo()));
            providers.add(p);
        }

        // Clinic locations
        List<Properties> clinicLocations = new ArrayList<>();
        for (ClinicLocation cl : clinicLocationDao.findAll()) {
            Properties p = new Properties();
            p.setProperty("clinic_location_name", nullToEmpty(cl.getClinicLocationName()));
            p.setProperty("clinic_location_no", nullToEmpty(cl.getClinicLocationNo()));
            clinicLocations.add(p);
        }

        // Resolve referral doctor name (from history's last referral_ohip if available)
        String referralDoctorName = demoLoad.referralDoctorName;
        if (referralDoctorOhip != null && !referralDoctorOhip.trim().isEmpty()) {
            ProfessionalSpecialist specialist = professionalSpecialistDao.getByReferralNo(referralDoctorOhip);
            if (specialist != null) {
                referralDoctorName = specialist.getLastName() + "," + specialist.getFirstName();
            }
        }

        // Default-value resolution for dxCode / visitType / clinicView / visitdate
        String dxCode = getDefaultValue(request.getParameter("dxCode"), billingHistoryDetails, "diagnostic_code");

        String xmlVisitType = getDefaultValue(request.getParameter("xml_visittype"), billingHistory, "visitType");
        if (!xmlVisitType.isEmpty()) {
            visitType = xmlVisitType;
        }
        String xmlLocation = getDefaultValue(request.getParameter("xml_location"), billingHistory, "clinic_ref_code");
        if (!xmlLocation.isEmpty()) {
            clinicView = xmlLocation;
        }
        String xmlVdate = getDefaultValue(request.getParameter("xml_vdate"), billingHistory, "visitdate");
        String visitDate = xmlVdate.isEmpty() ? "" : xmlVdate;

        // Service-code grid (3 columns) + premium flags
        Date now = new Date();
        Map<String, String> propPremium = new HashMap<>();
        ServiceCodeGroup g1 = loadServiceCodeGroup(ctlBillForm, "Group1", now, propPremium);
        ServiceCodeGroup g2 = loadServiceCodeGroup(ctlBillForm, "Group2", now, propPremium);
        ServiceCodeGroup g3 = loadServiceCodeGroup(ctlBillForm, "Group3", now, propPremium);

        // msg = res.getString("billing.hospitalBilling.msgDates") + errorMsg + warningMsg
        // The localized base lives in the JSP's ResourceBundle resolution; we pass the
        // accumulated error/warning portion and the JSP keeps the i18n prefix.
        String msg = demoLoad.errorMessage + demoLoad.warningMessage;

        return BillingShortcutPg1ViewModel.builder()
                .userProviderNo(userProviderNo)
                .providerView(providerView)
                .demoNo(demoNo)
                .demoName(demoName)
                .apptNo(apptNo)
                .apptProviderNo(apptProviderNo)
                .apptDate(apptDate)
                .startTime(startTime)
                .ctlBillForm(ctlBillForm)
                .clinicNo(clinicNo)
                .demoFirst(demoLoad.firstName)
                .demoLast(demoLoad.lastName)
                .demoSex(demoLoad.sex)
                .demoHin(demoLoad.hin)
                .demoDob(demoLoad.dob)
                .demoDobYy(demoLoad.dobYy)
                .demoDobMm(demoLoad.dobMm)
                .demoDobDd(demoLoad.dobDd)
                .demoHcType(demoLoad.hcType)
                .assignedProviderNo(assignedProviderNo)
                .referralDoctorName(referralDoctorName)
                .referralDoctorOhip(referralDoctorOhip)
                .visitType(visitType)
                .clinicView(clinicView)
                .visitDate(visitDate)
                .dxCode(dxCode)
                .errorFlag(demoLoad.errorFlag)
                .errorMessage(demoLoad.errorMessage)
                .warningMessage(demoLoad.warningMessage)
                .msg(msg)
                .billingHistory(billingHistory)
                .billingHistoryDetails(billingHistoryDetails)
                .providers(providers)
                .clinicLocations(clinicLocations)
                .serviceCodeCol1(g1.entries)
                .serviceCodeCol2(g2.entries)
                .serviceCodeCol3(g3.entries)
                .headerTitle1(g1.headerTitle)
                .headerTitle2(g2.headerTitle)
                .headerTitle3(g3.headerTitle)
                .propPremium(propPremium)
                .build();
    }

    private static final class ServiceCodeGroup {
        final List<Properties> entries;
        final String headerTitle;
        ServiceCodeGroup(List<Properties> entries, String headerTitle) {
            this.entries = entries;
            this.headerTitle = headerTitle;
        }
    }

    private ServiceCodeGroup loadServiceCodeGroup(String ctlBillForm,
                                                  String serviceGroup,
                                                  Date billReferenceDate,
                                                  Map<String, String> propPremium) {
        List<Properties> entries = new ArrayList<>();
        String headerTitle = "";
        for (Object[] o : billingServiceDao.findBillingServiceAndCtlBillingServiceByMagic(ctlBillForm, serviceGroup, billReferenceDate)) {
            BillingService b = (BillingService) o[0];
            CtlBillingService c = (CtlBillingService) o[1];
            Properties p = new Properties();
            headerTitle = nullToEmpty(c.getServiceGroupName());
            p.setProperty("serviceCode", nullToEmpty(b.getServiceCode()));
            p.setProperty("serviceDesc", nullToEmpty(b.getDescription()));
            p.setProperty("serviceDisp", nullToEmpty(b.getValue()));
            p.setProperty("servicePercentage", Misc.getStr(b.getPercentage(), ""));
            p.setProperty("serviceSLI", Misc.getStr("" + b.getSliFlag(), "false"));
            entries.add(p);
        }
        if (!entries.isEmpty()) {
            List<String> svcCodes = new ArrayList<>();
            for (Properties p : entries) {
                svcCodes.add(p.getProperty("serviceCode"));
            }
            for (CtlBillingServicePremium pr : ctlBillingServicePremiumDao.findByServceCodes(svcCodes)) {
                propPremium.put(pr.getServiceCode(), "A");
            }
        }
        return new ServiceCodeGroup(entries, headerTitle);
    }

    /**
     * Mirrors the legacy JSP's {@code getDefaultValue} helper: prefer the request
     * parameter, otherwise fall back to the first non-empty value of the named
     * key in the history vector.
     */
    private static String getDefaultValue(String paramValue, List<Properties> historyVec, String key) {
        if (paramValue != null && !paramValue.isEmpty()) {
            return paramValue;
        }
        for (Properties p : historyVec) {
            String v = p.getProperty(key, "");
            if (!v.isEmpty()) {
                return v;
            }
        }
        return "";
    }

    private DemographicLoad loadDemographic(String demoNo, String demoSexParam) {
        DemographicLoad load = new DemographicLoad();
        load.sex = nullToEmpty(demoSexParam);
        if (demoNo == null || demoNo.isEmpty()) {
            return load;
        }
        Demographic demo = demographicDao.getDemographic(demoNo);
        if (demo == null) {
            return load;
        }

        load.assignedProviderOverride = nullToEmpty(demo.getProviderNo());
        load.firstName = nullToEmpty(demo.getFirstName());
        load.lastName = nullToEmpty(demo.getLastName());

        String sex = nullToEmpty(demo.getSex());
        if (demo.getHin() != null && demo.getVer() != null) {
            load.hin = demo.getHin() + demo.getVer();
        }
        if ("M".equals(sex)) {
            sex = "1";
        } else if ("F".equals(sex)) {
            sex = "2";
        }
        load.sex = sex;

        String hcType = nullToEmpty(demo.getHcType());
        if (hcType.length() < 2) {
            hcType = "ON";
        } else {
            hcType = hcType.substring(0, 2).toUpperCase();
        }
        load.hcType = hcType;

        String dobYy = nullToEmpty(demo.getYearOfBirth());
        String dobMm = padTwo(nullToEmpty(demo.getMonthOfBirth()));
        String dobDd = padTwo(nullToEmpty(demo.getDateOfBirth()));
        load.dobYy = dobYy;
        load.dobMm = dobMm;
        load.dobDd = dobDd;
        load.dob = dobYy + dobMm + dobDd;

        if (demo.getFamilyDoctor() == null) {
            load.referralDoctorName = "N/A";
            load.referralDoctorOhip = "000000";
        } else {
            load.referralDoctorName = nullToEmpty(SxmlMisc.getXmlContent(demo.getFamilyDoctor(), "rd"));
            load.referralDoctorOhip = nullToEmpty(SxmlMisc.getXmlContent(demo.getFamilyDoctor(), "rdohip"));
        }

        StringBuilder error = new StringBuilder();
        StringBuilder warning = new StringBuilder();
        String errorFlag = "";

        if (demo.getHin() == null || demo.getHin().isEmpty()) {
            warning.append("<br><b><font color='orange'>Warning: The patient does not have a valid HIN. </font></b><br>");
        }
        if (!load.referralDoctorOhip.isEmpty() && load.referralDoctorOhip.length() != 6) {
            warning.append("<br><font color='orange'>Warning: the referral doctor's no is wrong. </font><br>");
        }
        if (load.dob.length() != 8) {
            errorFlag = "1";
            error.append("<br><b><font color='red'>Error: The patient does not have a valid DOB. </font></b><br>");
        }

        load.errorFlag = errorFlag;
        load.errorMessage = error.toString();
        load.warningMessage = warning.toString();
        return load;
    }

    private static class DemographicLoad {
        String firstName = "";
        String lastName = "";
        String sex = "";
        String hin = "";
        String dob = "";
        String dobYy = "";
        String dobMm = "";
        String dobDd = "";
        String hcType = "";
        String assignedProviderOverride = "";
        String referralDoctorName = "";
        String referralDoctorOhip = "";
        String errorFlag = "";
        String errorMessage = "";
        String warningMessage = "";
    }

    private static String padTwo(String v) {
        return v != null && v.length() == 1 ? "0" + v : v;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
