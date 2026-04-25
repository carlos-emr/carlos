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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.billings.ca.on.data.BillingONCorrectionViewModel;
import io.github.carlos_emr.carlos.billings.ca.on.data.JdbcBillingRAImpl;
import io.github.carlos_emr.carlos.commn.IsPropertiesOn;
import io.github.carlos_emr.carlos.commn.dao.BillingONCHeader1Dao;
import io.github.carlos_emr.carlos.commn.dao.ProfessionalSpecialistDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderSiteDao;
import io.github.carlos_emr.carlos.commn.dao.RaDetailDao;
import io.github.carlos_emr.carlos.commn.dao.SiteDao;
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
             SpringUtils.getBean(ProfessionalSpecialistDao.class));
    }

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
    }

    /**
     * Builds the user-context view model (provider record, site/team-access
     * flags, multisite list) and, if {@code billing_no}/{@code claim_no} is
     * present on the request, the bill-record fields too.
     */
    public BillingONCorrectionViewModel assemble(LoggedInInfo loggedInInfo, HttpServletRequest request) {
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

        loadBillRecord(loggedInInfo, request, builder, providerAccessList, mgrSites,
                siteAccessPrivacy, teamAccessPrivacy, multisites);

        return builder.build();
    }

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
    private void loadBillRecord(LoggedInInfo loggedInInfo,
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
            return;
        }

        Integer billingNo;
        try {
            billingNo = Integer.parseInt(billNo);
        } catch (NumberFormatException nfe) {
            b.billNoErr(true);
            return;
        }

        BillingONCHeader1 bCh1 = bCh1Dao.find(billingNo);
        if (bCh1 == null) {
            b.billNoErr(true);
            return;
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
            return;
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
        // a JdbcBillingRAImpl regression is visible to ops.
        try {
            JdbcBillingRAImpl raObj = new JdbcBillingRAImpl();
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
    }
}
