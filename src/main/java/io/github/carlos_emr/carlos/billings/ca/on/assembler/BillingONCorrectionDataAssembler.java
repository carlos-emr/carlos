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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.billings.ca.on.data.BillingDataHlp;
import io.github.carlos_emr.carlos.billings.ca.on.data.BillingMultisiteContext;
import io.github.carlos_emr.carlos.billings.ca.on.data.BillingONCorrectionViewModel;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingONLookupService;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingONRemittanceAdviceService;
import io.github.carlos_emr.carlos.commn.IsPropertiesOn;
import io.github.carlos_emr.carlos.commn.dao.BillingONCHeader1Dao;
import io.github.carlos_emr.carlos.commn.dao.BillingONEAReportDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONErrorCodeDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONExtDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONPaymentDao;
import io.github.carlos_emr.carlos.commn.dao.BillingServiceDao;
import io.github.carlos_emr.carlos.commn.dao.ClinicLocationDao;
import io.github.carlos_emr.carlos.commn.dao.ClinicNbrDao;
import io.github.carlos_emr.carlos.commn.dao.ProfessionalSpecialistDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderSiteDao;
import io.github.carlos_emr.carlos.commn.dao.RaDetailDao;
import io.github.carlos_emr.carlos.commn.dao.SiteDao;
import io.github.carlos_emr.carlos.commn.service.BillingONService;
import io.github.carlos_emr.carlos.commn.model.BillingONCHeader1;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.ProviderSite;
import io.github.carlos_emr.carlos.commn.model.RaDetail;
import io.github.carlos_emr.carlos.commn.model.Site;
import io.github.carlos_emr.carlos.demographic.data.DemographicData;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.util.DateUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.billings.ca.on.web.BillingCorrection2Action;

/**
 * Assembles {@link BillingONCorrectionViewModel} for
 * {@code billingONCorrection.jsp}. Brings the correction page in line with
 * the View2Action / Assembler / ViewModel layering already used by every
 * other refactored ON billing page (form, status, review, shortcutPg1).
 *
 * <p>Pure read: privilege flags + DAO lookups → DTO. Mutation logic stays in
 * {@link BillingCorrection2Action}; this assembler is invoked from the action
 * before any save/update branch decides what to return.</p>
 *
 * <p>Two responsibilities live here:</p>
 * <ul>
 *   <li><strong>User-context</strong>: the logged-in provider, their first /
 *       last name, the access-privacy flags, the multisite-access provider
 *       list (if {@code _site_access_privacy} is granted) and the team-billing
 *       flag.</li>
 *   <li><strong>Bill-record</strong>: when {@code billing_no} (or
 *       {@code claim_no} as a fallback) resolves to a {@link BillingONCHeader1}
 *       row, populate the bill, demographic, billing-date, claim-number, and
 *       referral-doctor fields with multisite/team-access enforcement. If the
 *       bill record is not requested, those fields stay empty (the JSP
 *       gates rendering on {@code isBillLoaded()}).</li>
 * </ul>
 *
 * @since 2026-04-25
 */
public final class BillingONCorrectionDataAssembler {

    private final SecurityInfoManager securityInfoManager;
    private final ProviderDao providerDao;
    private final ProviderSiteDao providerSiteDao;
    private final SiteDao siteDao;
    private final BillingONCHeader1Dao bCh1Dao;
    private final RaDetailDao raDetailDao;
    private final ProfessionalSpecialistDao professionalSpecialistDao;
    private final BillingONCorrectionRenderStep renderContextComposer;

    /**
     * Production constructor used by Struts; resolves dependencies from the
     * Spring context via {@link SpringUtils#getBean}. Tests use the
     * package-private constructor below to inject mocks directly.
     */
    public BillingONCorrectionDataAssembler() {
        this(SpringUtils.getBean(SecurityInfoManager.class),
             SpringUtils.getBean(ProviderDao.class),
             SpringUtils.getBean(ProviderSiteDao.class),
             SpringUtils.getBean(SiteDao.class),
             SpringUtils.getBean(BillingONCHeader1Dao.class),
             SpringUtils.getBean(RaDetailDao.class),
             SpringUtils.getBean(ProfessionalSpecialistDao.class),
             SpringUtils.getBean(BillingServiceDao.class),
             SpringUtils.getBean(BillingONService.class),
             SpringUtils.getBean(BillingONExtDao.class),
             SpringUtils.getBean(BillingONPaymentDao.class),
             SpringUtils.getBean(BillingONEAReportDao.class),
             SpringUtils.getBean(BillingONErrorCodeDao.class),
             SpringUtils.getBean(ClinicLocationDao.class),
             SpringUtils.getBean(ClinicNbrDao.class));
    }

    /**
     * Test constructor: 7-arg shape for legacy tests that only need
     * user-context + bill-record assembly. The render-context composer is
     * left {@code null}; {@link #assemble} skips it when null, mirroring
     * the test-mode short-circuit already used elsewhere in the billing
     * assembler family.
     */
    BillingONCorrectionDataAssembler(SecurityInfoManager securityInfoManager,
                                     ProviderDao providerDao,
                                     ProviderSiteDao providerSiteDao,
                                     SiteDao siteDao,
                                     BillingONCHeader1Dao bCh1Dao,
                                     RaDetailDao raDetailDao,
                                     ProfessionalSpecialistDao professionalSpecialistDao) {
        this.securityInfoManager = securityInfoManager;
        this.providerDao = providerDao;
        this.providerSiteDao = providerSiteDao;
        this.siteDao = siteDao;
        this.bCh1Dao = bCh1Dao;
        this.raDetailDao = raDetailDao;
        this.professionalSpecialistDao = professionalSpecialistDao;
        this.renderContextComposer = null;
    }

    BillingONCorrectionDataAssembler(SecurityInfoManager securityInfoManager,
                                     ProviderDao providerDao,
                                     ProviderSiteDao providerSiteDao,
                                     SiteDao siteDao,
                                     BillingONCHeader1Dao bCh1Dao,
                                     RaDetailDao raDetailDao,
                                     ProfessionalSpecialistDao professionalSpecialistDao,
                                     BillingServiceDao billingServiceDao,
                                     BillingONService billingONService,
                                     BillingONExtDao bExtDao,
                                     BillingONPaymentDao billingONPaymentDao,
                                     BillingONEAReportDao billingONEAReportDao,
                                     BillingONErrorCodeDao billingONErrorCodeDao,
                                     ClinicLocationDao clinicLocationDao,
                                     ClinicNbrDao clinicNbrDao) {
        this.securityInfoManager = securityInfoManager;
        this.providerDao = providerDao;
        this.providerSiteDao = providerSiteDao;
        this.siteDao = siteDao;
        this.bCh1Dao = bCh1Dao;
        this.raDetailDao = raDetailDao;
        this.professionalSpecialistDao = professionalSpecialistDao;
        this.renderContextComposer = new BillingONCorrectionRenderStep(
                securityInfoManager,
                billingServiceDao,
                billingONService,
                bExtDao,
                billingONPaymentDao,
                billingONEAReportDao,
                billingONErrorCodeDao,
                raDetailDao,
                clinicLocationDao,
                clinicNbrDao);
    }

    /**
     * Builds the user-context view model (provider record, site/team-access
     * flags, multisite list) and, if {@code billing_no}/{@code claim_no} is
     * present on the request, the bill-record fields too.
     */
    public BillingONCorrectionViewModel assemble(HttpServletRequest request, LoggedInInfo loggedInInfo) {
        String providerNo = loggedInInfo != null && loggedInInfo.getLoggedInProviderNo() != null
                ? loggedInInfo.getLoggedInProviderNo()
                : "";

        Provider userProvider = providerNo.isEmpty() ? null : providerDao.getProvider(providerNo);

        boolean siteAccessPrivacy = loggedInInfo != null
                && securityInfoManager.hasPrivilege(loggedInInfo, "_site_access_privacy", "r", null);
        boolean teamAccessPrivacy = loggedInInfo != null
                && securityInfoManager.hasPrivilege(loggedInInfo, "_team_access_privacy", "r", null);
        boolean teamBillingOnly = loggedInInfo != null
                && securityInfoManager.hasPrivilege(loggedInInfo, "_team_billing_only", "r", null);

        Set<String> providerAccessList = new HashSet<>();
        if (siteAccessPrivacy) {
            // Expand to every provider that shares a site with the logged-in
            // user: first resolve the user's site memberships, then for each
            // site pull every provider attached to it. The earlier shape only
            // looped findByProviderNo(providerNo), which re-added the user
            // themselves and silently hid bills from co-located providers.
            for (ProviderSite userSite : providerSiteDao.findByProviderNo(providerNo)) {
                int siteId = userSite.getId().getSiteId();
                for (ProviderSite siteMember : providerSiteDao.findBySiteId(siteId)) {
                    providerAccessList.add(siteMember.getId().getProviderNo());
                }
            }
        }
        if (teamAccessPrivacy && userProvider != null) {
            for (Provider p : providerDao.getBillableProvidersOnTeam(userProvider)) {
                providerAccessList.add(p.getProviderNo());
            }
        }

        boolean multisites = IsPropertiesOn.isMultisitesEnable();
        List<String> mgrSites = new ArrayList<>();
        if (multisites) {
            for (Site s : siteDao.getActiveSitesByProviderNo(providerNo)) {
                mgrSites.add(s.getName());
            }
        }

        BillingONCorrectionViewModel.Builder builder = BillingONCorrectionViewModel.builder()
                .userProviderNo(providerNo)
                .userFirstName(userProvider != null ? userProvider.getFirstName() : "")
                .userLastName(userProvider != null ? userProvider.getLastName() : "")
                .siteAccessPrivacy(siteAccessPrivacy)
                .teamAccessPrivacy(teamAccessPrivacy)
                .teamBillingOnly(teamBillingOnly)
                .multisites(multisites)
                .providerAccessList(providerAccessList)
                .mgrSites(mgrSites);

        BillRecordContext billCtx = loadBillRecord(loggedInInfo, request, builder, providerAccessList, mgrSites,
                siteAccessPrivacy, teamAccessPrivacy, multisites);

        if (renderContextComposer != null) {
            renderContextComposer.compose(builder, request, loggedInInfo,
                    billCtx.bCh1, billCtx.billNo, billCtx.multiSiteProvider, billCtx.payProgram);
            // Render-helpers (multisite per-site provider HTML, BillingDataHlp
            // payment-type pairs, BillingONLookupService non-multisite provider list,
            // request-param echoes, resolved current site) live in this branch
            // because they depend on JDBC + static helpers the 7-arg test
            // constructor isn't expected to wire up. Tests that exercise them
            // construct via the full ctor and stub the relevant downstream calls.
            populateRenderHelpers(builder, request, providerNo, siteAccessPrivacy, teamAccessPrivacy,
                    teamBillingOnly, multisites, billCtx);
        }

        return builder.build();
    }

    /**
     * Populate the JSP render-helper fields that the legacy scriptlet block
     * computed inline (multisite per-site provider HTML, non-multisite
     * provider option list, BillingDataHlp.vecPaymentType pairs, request-param
     * echoes for {@code admin}/{@code adminSubmit}, and the resolved current
     * site value for the multisite picker's {@code selected} attribute).
     */
    private void populateRenderHelpers(BillingONCorrectionViewModel.Builder b,
                                       HttpServletRequest request,
                                       String userProviderNo,
                                       boolean siteAccessPrivacy,
                                       boolean teamAccessPrivacy,
                                       boolean teamBillingOnly,
                                       boolean multisites,
                                       BillRecordContext billCtx) {
        // ---- multisite sites + provider HTML ----
        if (multisites && userProviderNo != null && !userProviderNo.isEmpty()) {
            List<Site> sites = siteDao.getActiveSitesByProviderNo(userProviderNo);
            List<BillingMultisiteContext.MultisiteSite> msites = new ArrayList<>();
            Map<String, String> siteHtml = new LinkedHashMap<>();
            for (Site site : sites) {
                Set<Provider> siteProviderSet = site.getProviders();
                List<Provider> siteProvidersList = new ArrayList<>(siteProviderSet);
                Collections.sort(siteProvidersList, new Provider().ComparatorName());
                List<BillingMultisiteContext.MultisiteProvider> mProvs = new ArrayList<>();
                StringBuilder html = new StringBuilder();
                for (Provider p : siteProvidersList) {
                    if ("1".equals(p.getStatus()) && StringUtils.isNotBlank(p.getOhipNo())) {
                        // Cross-cutting record carries ohipNo too — Correction
                        // doesn't currently use it, so we propagate the actual
                        // provider OHIP for forward-compat.
                        mProvs.add(new BillingMultisiteContext.MultisiteProvider(
                                nullToEmpty(p.getProviderNo()),
                                nullToEmpty(p.getOhipNo()),
                                nullToEmpty(p.getLastName()),
                                nullToEmpty(p.getFirstName())));
                        html.append("<option value='")
                                .append(escapeForHtmlAttr(nullToEmpty(p.getProviderNo())))
                                .append("'>")
                                .append(escapeForHtml(nullToEmpty(p.getLastName())))
                                .append(", ")
                                .append(escapeForHtml(nullToEmpty(p.getFirstName())))
                                .append("</option>");
                    }
                }
                msites.add(new BillingMultisiteContext.MultisiteSite(
                        nullToEmpty(site.getName()),
                        nullToEmpty(site.getBgColor()),
                        mProvs));
                siteHtml.put(nullToEmpty(site.getName()), html.toString());
            }
            b.multisiteSites(msites);
            b.multisiteProviderHtml(siteHtml);
        }

        // ---- non-multisite provider list ----
        // Mirrors legacy scriptlet's tri-branch over BillingONLookupService.
        List<String> pList;
        BillingONLookupService util = SpringUtils.getBean(BillingONLookupService.class);
        if (teamBillingOnly || teamAccessPrivacy) {
            pList = util.getCurTeamProviderStr(userProviderNo);
        } else if (siteAccessPrivacy) {
            pList = util.getCurSiteProviderStr(userProviderNo);
        } else {
            pList = util.getCurProviderStr();
        }
        List<BillingONCorrectionViewModel.ProviderOption> providerOptions = new ArrayList<>();
        if (pList != null) {
            for (String entry : pList) {
                if (entry == null) {
                    continue;
                }
                String[] parts = entry.split("\\|");
                if (parts.length >= 3) {
                    providerOptions.add(new BillingONCorrectionViewModel.ProviderOption(
                            parts[0], parts[2], parts[1]));
                } else if (parts.length == 2) {
                    providerOptions.add(new BillingONCorrectionViewModel.ProviderOption(
                            parts[0], "", parts[1]));
                } else if (parts.length == 1) {
                    providerOptions.add(new BillingONCorrectionViewModel.ProviderOption(
                            parts[0], "", ""));
                }
            }
        }
        b.providerOptions(providerOptions);

        // ---- payment type code/label pairs ----
        Vector<?> raw = BillingDataHlp.vecPaymentType;
        List<BillingONCorrectionViewModel.PaymentTypeEntry> paymentTypes = new ArrayList<>();
        for (int i = 0; i + 1 < raw.size(); i += 2) {
            paymentTypes.add(new BillingONCorrectionViewModel.PaymentTypeEntry(
                    String.valueOf(raw.get(i)), String.valueOf(raw.get(i + 1))));
        }
        b.paymentTypes(paymentTypes);

        // ---- request-param echoes (for ?adminSubmit / ?admin / ?site UI gating) ----
        Map<String, String> echoes = new HashMap<>();
        for (String name : new String[]{"adminSubmit", "admin", "site"}) {
            String v = request.getParameter(name);
            if (v != null) {
                echoes.put(name, v);
            }
        }
        b.requestParamEchoes(echoes);

        // ---- resolved current site (?site request param overrides bill clinicSite) ----
        String siteParam = request.getParameter("site");
        String resolvedSite = siteParam == null ? nullToEmpty(billCtx.clinicSite()) : siteParam;
        b.currentSite(resolvedSite);
    }

    private static String escapeForHtmlAttr(String v) {
        return io.github.carlos_emr.carlos.utility.SafeEncode.forHtmlAttribute(v);
    }

    private static String escapeForHtml(String v) {
        return io.github.carlos_emr.carlos.utility.SafeEncode.forHtmlContent(v);
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }

    /**
     * Internal carrier for the bill-record state {@link #loadBillRecord}
     * needs to forward to the render-context composer (the loaded
     * {@link BillingONCHeader1}, the parsed billing number, the multi-site
     * access verdict, and the bill's pay program). Avoids leaking these
     * across method boundaries via the builder.
     */
    private record BillRecordContext(BillingONCHeader1 bCh1, String billNo,
                                     boolean multiSiteProvider, String payProgram,
                                     String clinicSite) {}

    /**
     * Loads the bill record (BillingONCHeader1) referenced by the {@code billing_no}
     * (or fallback {@code claim_no}) request param and populates the bill-record
     * fields on the view model.
     *
     * <p>If neither {@code billing_no} nor {@code claim_no} is present, the model
     * stays empty (corresponds to the legacy "form not loaded" path). If the
     * billing_no is present but doesn't resolve to a bill, the model surfaces
     * {@code billNoErr=true} which the JSP renders as "Invoice number does
     * not exist!".</p>
     *
     * <p>Multisite/team access is enforced here: a provider who lacks access
     * to the bill's clinic site or to the bill provider's team gets
     * {@code multiSiteProvider=false} and the patient fields stay empty (the
     * JSP shows an "access denied" alert).</p>
     */
    private BillRecordContext loadBillRecord(LoggedInInfo loggedInInfo,
                                             HttpServletRequest request,
                                             BillingONCorrectionViewModel.Builder b,
                                             Set<String> providerAccessList,
                                             List<String> mgrSites,
                                             boolean siteAccessPrivacy,
                                             boolean teamAccessPrivacy,
                                             boolean multisites) {
        String billNoParam = request.getParameter("billing_no");
        String claimNoParam = request.getParameter("claim_no");
        if (claimNoParam != null && claimNoParam.equals("null")) {
            claimNoParam = null;
        }

        String billNo = billNoParam == null ? "" : billNoParam.trim();
        String claimNo = claimNoParam == null ? "" : claimNoParam.trim();

        // claim_no fallback: resolve billing_no via RaDetailDao if billing_no missing
        if (billNo.isEmpty() && !claimNo.isEmpty()) {
            List<RaDetail> raDetails = raDetailDao.getRaDetailByClaimNo(claimNo);
            if (!raDetails.isEmpty()) {
                billNo = String.valueOf(raDetails.get(0).getBillingNo());
            }
        }

        b.billingNo(billNo).claimNo(claimNo);

        if (billNo.isEmpty()) {
            return new BillRecordContext(null, billNo, false, "", "");
        }

        Integer billingNo;
        try {
            billingNo = Integer.parseInt(billNo);
        } catch (NumberFormatException nfe) {
            b.billNoErr(true);
            return new BillRecordContext(null, billNo, false, "", "");
        }

        BillingONCHeader1 bCh1 = bCh1Dao.find(billingNo);
        if (bCh1 == null) {
            b.billNoErr(true);
            return new BillRecordContext(null, billNo, false, "", "");
        }

        b.billLoaded(true);

        String clinicSite = bCh1.getClinic() == null ? "" : bCh1.getClinic();
        b.clinicSite(clinicSite);

        // Multisite / team-billing access guard
        boolean multiSiteProvider = true;
        if ((siteAccessPrivacy || teamAccessPrivacy) && !providerAccessList.contains(bCh1.getProviderNo())) {
            multiSiteProvider = false;
        }
        if (multisites && !mgrSites.contains(clinicSite)) {
            multiSiteProvider = false;
        }
        b.multiSiteProvider(multiSiteProvider);
        b.manReview(bCh1.getManReview() == null ? "" : bCh1.getManReview());

        if (!multiSiteProvider) {
            // Access denied — leave patient fields empty; JSP shows alert.
            // Render-context composer still gets bCh1 + payProgram so it can
            // build the (empty) third-party totals consistent with the
            // legacy scriptlet, which always rendered the htmlPaid block.
            return new BillRecordContext(bCh1, billNo, false,
                    bCh1.getPayProgram() == null ? "" : bCh1.getPayProgram(), clinicSite);
        }

        Locale locale = request.getLocale();
        b.createTimestamp(DateUtils.formatDateTime(bCh1.getTimestamp(), locale));
        b.demoNo(bCh1.getDemographicNo() == null ? "" : bCh1.getDemographicNo().toString())
                .demoName(bCh1.getDemographicName() == null ? "" : bCh1.getDemographicName())
                .demoDob(bCh1.getDob() == null ? "" : bCh1.getDob());

        String demoSex = "";
        if (bCh1.getSex() != null) {
            demoSex = bCh1.getSex().equals("1") ? "M" : "F";
        }
        b.demoSex(demoSex);

        String hin = "";
        String demoRosterStatus = "";
        if (bCh1.getDemographicNo() != null) {
            try {
                Demographic sdemo = new DemographicData().getDemographic(loggedInInfo, bCh1.getDemographicNo().toString());
                if (sdemo != null) {
                    hin = (sdemo.getHin() == null ? "" : sdemo.getHin())
                            + (sdemo.getVer() == null ? "" : sdemo.getVer());
                    String dobYy = sdemo.getYearOfBirth() == null ? "" : sdemo.getYearOfBirth();
                    String dobMm = sdemo.getMonthOfBirth() == null ? "" : sdemo.getMonthOfBirth();
                    String dobDd = sdemo.getDateOfBirth() == null ? "" : sdemo.getDateOfBirth();
                    b.demoDob(dobYy + dobMm + dobDd);
                    if (sdemo.getSex() != null) {
                        b.demoSex(sdemo.getSex());
                    }
                    demoRosterStatus = sdemo.getRosterStatus() == null ? "" : sdemo.getRosterStatus();
                }
            } catch (RuntimeException e) {
                // Empty patient context renders without a banner today; surface
                // a flag so the JSP shows ops "demographic load failed" rather
                // than letting the operator act on the (empty) context as if
                // it were authoritative. Logged at ERROR because a failed
                // demographic load is data-integrity, not a transient fetch.
                b.demoLoadError(true);
                MiscUtils.getLogger().error(
                        "Demographic load failed for bill {} demoNo={}; rendering correction page with empty patient context",
                        billNo, bCh1.getDemographicNo(), e);
            }
        }
        b.hin(hin).demoRosterStatus(demoRosterStatus);

        b.billLocationNo(bCh1.getFaciltyNum() == null ? "" : bCh1.getFaciltyNum());
        b.billDate(DateUtils.formatDate(bCh1.getBillingDate(), locale));
        b.billProvider(bCh1.getProviderNo() == null ? "" : bCh1.getProviderNo());
        b.billStatus(bCh1.getStatus() == null ? "" : bCh1.getStatus());
        b.payProgram(bCh1.getPayProgram() == null ? "" : bCh1.getPayProgram());
        b.billTotal(bCh1.getTotal() == null ? "" : bCh1.getTotal().toPlainString());

        try {
            b.visitDate(DateUtils.formatDate(bCh1.getAdmissionDate(), locale));
        } catch (java.text.ParseException e) {
            // Stored admission_date is unparseable. Surface a flag so the
            // JSP shows ops a banner — mirrors demoLoadError / raLookupError
            // sibling branches in this method which also flag + log on
            // load-side data corruption rather than swallowing silently.
            b.visitDate("");
            b.visitDateInvalid(true);
            MiscUtils.getLogger().warn(
                    "Bill {} has unparseable admission_date; rendering empty visitDate",
                    billNo, e);
        }
        b.visitType(bCh1.getVisitType() == null ? "" : bCh1.getVisitType());
        b.sliCode(bCh1.getLocation() == null ? "" : bCh1.getLocation());
        b.hcType(bCh1.getProvince() == null ? "" : bCh1.getProvince());
        b.hcSex(bCh1.getSex() == null ? "" : bCh1.getSex());

        // Referral doctor from ohip number
        String rDoctorOhip = bCh1.getRefNum() == null ? "" : bCh1.getRefNum();
        b.referralDoctorOhip(rDoctorOhip);
        if (!rDoctorOhip.isEmpty()) {
            List<ProfessionalSpecialist> specialists = professionalSpecialistDao.findByReferralNo(rDoctorOhip);
            if (specialists != null && !specialists.isEmpty()) {
                ProfessionalSpecialist sp = specialists.get(0);
                b.referralDoctor((sp.getLastName() == null ? "" : sp.getLastName())
                        + ", " + (sp.getFirstName() == null ? "" : sp.getFirstName()));
            }
        }

        b.comment(bCh1.getComment() == null ? "" : bCh1.getComment());

        // OHIP RA claim number — primary correlation key for ministry
        // remittance. Surface a flag so the JSP shows "RA lookup unavailable"
        // rather than silently rendering an empty claimNo. Log at ERROR so
        // a BillingONRemittanceAdviceService regression is visible to ops.
        try {
            BillingONRemittanceAdviceService raObj = SpringUtils.getBean(BillingONRemittanceAdviceService.class);
            String raClaim = raObj.getRAClaimNo4BillingNo(billNo);
            if (raClaim != null) {
                b.claimNo(raClaim);
            }
        } catch (RuntimeException e) {
            b.raLookupError(true);
            MiscUtils.getLogger().error(
                    "RA claim lookup failed for bill {}; correction page will show empty claimNo",
                    billNo, e);
        }

        return new BillRecordContext(bCh1, billNo, true,
                bCh1.getPayProgram() == null ? "" : bCh1.getPayProgram(), clinicSite);
    }
}
