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

import jakarta.servlet.http.HttpServletRequest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.billings.ca.on.BillingAmounts;
import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingClaimHeaderDto;
import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingErrorReportDto;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingMultisiteContext;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingOnStatusViewModel;
import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingProviderDto;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingOnErrorReportService;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingOnLookupService;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingRaLookupService;
import io.github.carlos_emr.carlos.commn.IsPropertiesOn;
import io.github.carlos_emr.carlos.commn.dao.SiteDao;
import io.github.carlos_emr.carlos.commn.model.BillingONCHeader1;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.Site;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.util.DateUtils;
import io.github.carlos_emr.carlos.util.LabelValueBean;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingStatusLoader;
import io.github.carlos_emr.carlos.billings.ca.on.web.ViewBillingOnStatus2Action;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Assembles {@link BillingOnStatusViewModel} for {@code billingONStatus.jsp}.
 *
 * <p>Extracted from {@link ViewBillingOnStatus2Action} so the action stays a
 * thin gate (security check + assembler invocation) and the parameter-echo +
 * default-resolution + DB-fan-out logic is testable in isolation. Spring-wired
 * via single-constructor injection of all collaborators, so the assembler is
 * driveable from a unit test by passing mocks directly.</p>
 *
 * @since 2026-04-25
 */
@org.springframework.stereotype.Service
public class BillingOnStatusViewModelAssembler {

    private final SecurityInfoManager securityInfoManager;
    private final BillingOnLookupService lookupService;
    private final BillingStatusLoader statusPrep;
    private final BillingOnErrorReportService errorRepImpl;
    private final SiteDao siteDao;
    private final BillingRaLookupService raLookupService;

    public BillingOnStatusViewModelAssembler(SecurityInfoManager securityInfoManager,
                                 BillingOnLookupService lookupService,
                                 BillingStatusLoader statusPrep,
                                 BillingOnErrorReportService errorRepImpl,
                                 SiteDao siteDao,
                                 BillingRaLookupService raLookupService) {
        this.securityInfoManager = securityInfoManager;
        this.lookupService = lookupService;
        this.statusPrep = statusPrep;
        this.errorRepImpl = errorRepImpl;
        this.siteDao = siteDao;
        this.raLookupService = raLookupService;
    }

    /**
     * Builds the status-page view model from request parameters and the
     * logged-in user's privilege flags. Pure read; no side effects on
     * persisted state.
     */
    public BillingOnStatusViewModel assemble(HttpServletRequest request, LoggedInInfo loggedInInfo) {
        boolean teamBillingOnly = securityInfoManager
                .hasPrivilege(loggedInInfo, "_team_billing_only", "r", null);
        boolean siteAccessPrivacy = securityInfoManager
                .hasPrivilege(loggedInInfo, "_site_access_privacy", "r", null);

        CarlosProperties props = CarlosProperties.getInstance();
        boolean hideName = Boolean.parseBoolean(
                props.getProperty("invoice_reports.print.hide_name", "false"));
        boolean multisitesEnabled = IsPropertiesOn.isMultisitesEnable();

        String[] billTypeParam = request.getParameterValues("billType");
        boolean search = billTypeParam != null && billTypeParam.length > 0;
        String[] billTypes = search
                ? billTypeParam
                : BillingOnStatusViewModel.DEFAULT_BILL_TYPES.toArray(new String[0]);

        String statusType = firstNonNull(request.getParameter("statusType"), "O");
        String demoNo = firstNonNull(request.getParameter("demographicNo"), "");
        if ("_".equals(statusType)) {
            demoNo = "";
        }
        String filename = demoNo;

        String startDate = firstNonEmpty(request.getParameter("xml_vdate"),
                DateUtils.sumDate("yyyy-MM-dd", "-180"));
        String endDate = firstNonEmpty(request.getParameter("xml_appointment_date"),
                DateUtils.sumDate("yyyy-MM-dd", "0"));

        String providerNo = firstNonNull(request.getParameter("providerview"), "");
        String providerOhipNo = firstNonNull(request.getParameter("provider_ohipNo"), "");
        String raCode = firstNonNull(request.getParameter("raCode"), "");
        String claimNo = firstNonNull(request.getParameter("claimNo"), "");
        String dx = firstNonNull(request.getParameter("dx"), "");
        String visitType = firstNonNull(request.getParameter("visitType"), "-");

        // Track whether the request actually carried these filters so we can
        // route ad-hoc / URL-navigated calls (without the filter params) to
        // the simpler getBills() DAO path below — matching the legacy
        // scriptlet's behavior. Once normalized to "%"/"---" the original
        // intent is no longer recoverable, so capture it before defaulting.
        String rawServiceCode = request.getParameter("serviceCode");
        boolean serviceCodeFilterAbsent = rawServiceCode == null || rawServiceCode.isEmpty();
        String serviceCode = serviceCodeFilterAbsent ? "%" : rawServiceCode;

        // Legacy "any billing form" sentinel is three dashes; a single "-" is a
        // real value in some installations and would mis-filter the search.
        String rawBillingForm = request.getParameter("billing_form");
        boolean billingFormFilterAbsent = rawBillingForm == null;
        String billingForm = billingFormFilterAbsent ? "---" : rawBillingForm;
        String visitLocation = firstNonNull(request.getParameter("xml_location"), "");
        String selectedSite = request.getParameter("site");
        String sortName = firstNonNull(request.getParameter("sortName"), "ServiceDate");
        String sortOrder = firstNonNull(request.getParameter("sortOrder"), "asc");
        String paymentStartDate = firstNonNull(request.getParameter("paymentStartDate"), "");
        String paymentEndDate = firstNonNull(request.getParameter("paymentEndDate"), "");

        // ---- provider list ----
        String sessionUser = loggedInInfo == null ? null : loggedInInfo.getLoggedInProviderNo();
        if (sessionUser == null && request.getSession(false) != null) {
            Object u = request.getSession().getAttribute("user");
            if (u instanceof String) sessionUser = (String) u;
        }
        List<io.github.carlos_emr.carlos.billings.ca.on.dto.ProviderDropdownEntry> pList = teamBillingOnly
                ? lookupService.getCurTeamProviderStr(sessionUser)
                : lookupService.getCurProviderStr();
        if (pList == null) {
            pList = Collections.emptyList();
        }
        List<BillingOnStatusViewModel.ProviderOption> providers = new ArrayList<>(pList.size());
        for (io.github.carlos_emr.carlos.billings.ca.on.dto.ProviderDropdownEntry entry : pList) {
            providers.add(new BillingOnStatusViewModel.ProviderOption(
                    entry.providerNo(), entry.lastName(), entry.firstName(), entry.ohipNo()));
        }

        // ---- multisite siteBgColor / siteShortName lookup maps + per-site
        //      provider option lists ----
        Map<String, String> siteBgColorMap = new HashMap<>();
        Map<String, String> siteShortNameMap = new HashMap<>();
        List<BillingMultisiteContext.MultisiteSite> multisiteSites = new ArrayList<>();
        if (multisitesEnabled) {
            if (siteDao != null) {
                List<Site> allSites = siteDao.getAllSites();
                if (allSites != null) {
                    for (Site st : allSites) {
                        siteBgColorMap.put(st.getName(), st.getBgColor());
                        siteShortNameMap.put(st.getName(), st.getShortName());
                    }
                }
                List<Site> activeSites = sessionUser == null
                        ? Collections.<Site>emptyList()
                        : siteDao.getActiveSitesByProviderNo(sessionUser);
                if (activeSites == null) {
                    activeSites = Collections.emptyList();
                }
                Set<String> allowedProviders = new HashSet<>();
                for (io.github.carlos_emr.carlos.billings.ca.on.dto.ProviderDropdownEntry entry : pList) {
                    allowedProviders.add(entry.providerNo());
                }
                for (Site site : activeSites) {
                    Set<Provider> siteProviders = site.getProviders();
                    List<Provider> sortedProviders = siteProviders == null
                            ? Collections.<Provider>emptyList()
                            : new ArrayList<>(siteProviders);
                    if (!sortedProviders.isEmpty()) {
                        Collections.sort(sortedProviders, (new Provider()).ComparatorName());
                    }
                    List<BillingMultisiteContext.MultisiteProvider> providerOpts =
                            new ArrayList<>();
                    for (Provider p : sortedProviders) {
                        if (allowedProviders.contains(p.getProviderNo())) {
                            // Status doesn't expose ohipNo on the multisite
                            // option list, so the cross-cutting record's
                            // ohipNo slot is left empty.
                            providerOpts.add(
                                    new BillingMultisiteContext.MultisiteProvider(
                                            p.getProviderNo(),
                                            "",
                                            p.getLastName(),
                                            p.getFirstName()));
                        }
                    }
                    multisiteSites.add(new BillingMultisiteContext.MultisiteSite(
                            site.getName(), site.getBgColor(), providerOpts));
                }
            }
        }

        // ---- billing forms list ----
        List<LabelValueBean> formList = statusPrep.listBillingForms();
        List<BillingOnStatusViewModel.BillingFormOption> billingForms = new ArrayList<>();
        if (formList != null) {
            for (LabelValueBean lv : formList) {
                billingForms.add(new BillingOnStatusViewModel.BillingFormOption(
                        lv.getLabel(), lv.getValue()));
            }
        }

        // ---- visit-location list ----
        List<BillingOnStatusViewModel.VisitLocationOption> visitLocations = new ArrayList<>();
        @SuppressWarnings("rawtypes")
        List facilityNums = lookupService.getFacilty_num();
        if (facilityNums != null) {
            for (int i = 0; i < facilityNums.size() - 1; i += 2) {
                String code = (String) facilityNums.get(i);
                String label = (String) facilityNums.get(i + 1);
                visitLocations.add(new BillingOnStatusViewModel.VisitLocationOption(code, label));
            }
        }

        // ---- bill list (rejected vs sorted) ----
        // Legacy semantics: when the request omitted either the serviceCode or
        // the billing_form filter (and dx/visitType are short), use the
        // simpler getBills() query that doesn't take those filter dimensions.
        // After normalization above the params are always "%"/"---", so we
        // route on the captured-before-defaulting flags rather than on the
        // string values.
        List<BillingClaimHeaderDto> bList;
        if ((serviceCodeFilterAbsent || billingFormFilterAbsent)
                && dx.length() < 2 && visitType.length() < 2) {
            // deepcode ignore SqlInjection: BillingStatusLoader delegates to
            // BillingOnClaimLoader which uses JPA criteria queries (parameterized)
            bList = search
                    ? statusPrep.getBills(billTypes, statusType, providerNo, startDate, endDate,
                            demoNo, visitLocation, paymentStartDate, paymentEndDate)
                    : new ArrayList<BillingClaimHeaderDto>();
            serviceCode = "%";
        } else {
            String effectiveServiceCode = (serviceCode == null || serviceCode.length() < 2)
                    ? "%" : serviceCode;
            bList = search
                    ? statusPrep.getBillsWithSorting(billTypes, statusType, providerNo, startDate,
                            endDate, demoNo, effectiveServiceCode, dx, visitType, billingForm,
                            visitLocation, sortName, sortOrder, paymentStartDate, paymentEndDate,
                            claimNo)
                    : new ArrayList<BillingClaimHeaderDto>();
            serviceCode = effectiveServiceCode;
        }

        // ---- rejected-bill rows ("_") ----
        List<BillingOnStatusViewModel.RejectedBillRow> rejectedRows = "_".equals(statusType)
                ? buildRejectedRows(pList, providerNo, startDate, endDate, filename)
                : Collections.<BillingOnStatusViewModel.RejectedBillRow>emptyList();

        // ---- normal bill rows + aggregate totals ----
        BillRowAggregate agg = buildBillRows(bList, multisitesEnabled, selectedSite,
                siteAccessPrivacy, raCode, serviceCode, siteBgColorMap, siteShortNameMap);

        // ---- request param echoes ----
        Map<String, String> echoes = new HashMap<>();
        for (String name : new String[]{"site", "providerview"}) {
            String v = request.getParameter(name);
            if (v != null) echoes.put(name, v);
        }

        return BillingOnStatusViewModel.builder()
                .teamBillingOnly(teamBillingOnly)
                .siteAccessPrivacy(siteAccessPrivacy)
                .multisites(multisitesEnabled)
                .hideName(hideName)
                .search(search)
                .billTypes(billTypes)
                .statusType(statusType)
                .demoNo(demoNo)
                .filename(filename)
                .startDate(startDate)
                .endDate(endDate)
                .providerNo(providerNo)
                .providerOhipNo(providerOhipNo)
                .raCode(raCode)
                .claimNo(claimNo)
                .dx(dx)
                .visitType(visitType)
                .serviceCode(serviceCode)
                .billingForm(billingForm)
                .visitLocation(visitLocation)
                .selectedSite(selectedSite)
                .sortName(sortName)
                .sortOrder(sortOrder)
                .paymentStartDate(paymentStartDate)
                .paymentEndDate(paymentEndDate)
                .endDateMinus30(DateUtils.sumDate("yyyy-MM-dd", "-30"))
                .endDateMinus60(DateUtils.sumDate("yyyy-MM-dd", "-60"))
                .endDateMinus90(DateUtils.sumDate("yyyy-MM-dd", "-90"))
                .providers(providers)
                .multisiteSites(multisiteSites)
                .siteBgColor(siteBgColorMap)
                .siteShortName(siteShortNameMap)
                .billingForms(billingForms)
                .visitLocations(visitLocations)
                .rejectedBillRows(rejectedRows)
                .billRows(agg.rows())
                .patientCount(agg.patientCount())
                .unreadableTotalRowCount(agg.unreadableTotalRowCount())
                .totalBilled(agg.totalBilled())
                .totalPaid(agg.totalPaid())
                .totalAdjustments(agg.totalAdjustments())
                .totalCash(agg.totalCash())
                .totalDebit(agg.totalDebit())
                .requestParamEchoes(echoes)
                .build();
    }

    private List<BillingOnStatusViewModel.RejectedBillRow> buildRejectedRows(
            List<io.github.carlos_emr.carlos.billings.ca.on.dto.ProviderDropdownEntry> pList,
            String providerNo, String startDate, String endDate,
            String filename) {
        List<String> aLProviders = new ArrayList<>();
        if (providerNo == null || providerNo.isEmpty()) {
            for (io.github.carlos_emr.carlos.billings.ca.on.dto.ProviderDropdownEntry entry : pList) {
                aLProviders.add(entry.providerNo());
            }
        } else {
            aLProviders.add(providerNo);
        }
        List<BillingOnStatusViewModel.RejectedBillRow> rows = new ArrayList<>();
        for (String entry : aLProviders) {
            String currentProvider = entry == null ? "" : entry.trim();
            @SuppressWarnings("rawtypes")
            List lPat;
            if ("all".equals(currentProvider)) {
                List<BillingProviderDto> providerObjs = lookupService.getProviderObjList(currentProvider);
                lPat = errorRepImpl.getErrorRecords(providerObjs, startDate, endDate, filename);
            } else {
                BillingProviderDto providerObj = lookupService.getProviderObj(currentProvider);
                lPat = errorRepImpl.getErrorRecords(providerObj, startDate, endDate, filename);
            }
            if (lPat == null) continue;
            boolean nC = false;
            String invoiceNo = "";
            for (Object raw : lPat) {
                BillingErrorReportDto bObj = (BillingErrorReportDto) raw;
                if (!safeEquals(invoiceNo, bObj.getBilling_no())) {
                    invoiceNo = bObj.getBilling_no();
                    nC = !nC;
                }
                String rowClass = nC ? "success" : "";
                String formattedFee;
                boolean feeUnreadable = false;
                try {
                    formattedFee = BillingAmounts.format(BillingAmounts.amount(bObj.getFee()));
                } catch (RuntimeException nfe) {
                    feeUnreadable = true;
                    MiscUtils.getLogger().warn("Rejected-bill fee is not numeric for billingNo={} fee={}",
                            LogSafe.sanitize(bObj.getBilling_no()),
                            LogSafe.sanitize(bObj.getFee()), nfe);
                    formattedFee = "N/A";
                }
                String stdCurr = formattedFee;
                boolean checked = !BillingONCHeader1.NOT_BILLED.equals(bObj.getStatus());
                rows.add(new BillingOnStatusViewModel.RejectedBillRow(
                        bObj.getId(),
                        bObj.getHin(),
                        bObj.getVer(),
                        bObj.getDob(),
                        bObj.getBilling_no(),
                        bObj.getRef_no(),
                        bObj.getFacility(),
                        bObj.getAdmitted_date(),
                        bObj.getClaim_error(),
                        bObj.getCode(),
                        stdCurr,
                        bObj.getUnit(),
                        bObj.getCode_date(),
                        bObj.getDx(),
                        bObj.getExp(),
                        bObj.getCode_error(),
                        bObj.getReport_name(),
                        feeUnreadable,
                        checked,
                        rowClass));
            }
        }
        return rows;
    }

    private record BillRowAggregate(
            List<BillingOnStatusViewModel.BillRow> rows,
            int patientCount,
            String totalBilled,
            String totalPaid,
            String totalAdjustments,
            String totalCash,
            String totalDebit,
            int unreadableTotalRowCount) { }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    private BillRowAggregate buildBillRows(
            List<BillingClaimHeaderDto> bList, boolean multisitesEnabled, String selectedSite,
            boolean siteAccessPrivacy, String raCode, String serviceCode,
            Map<String, String> siteBgColor, Map<String, String> siteShortName) {
        List<BillingOnStatusViewModel.BillRow> rows = new ArrayList<>();
        int patientCount = 0;
        int unreadableTotalRowCount = 0;
        BigDecimal total = new BigDecimal("0").setScale(2, RoundingMode.HALF_UP);
        BigDecimal paidTotal = new BigDecimal("0").setScale(2, RoundingMode.HALF_UP);
        BigDecimal adjTotal = new BigDecimal("0").setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalCash = BigDecimal.ZERO;
        BigDecimal totalDebit = BigDecimal.ZERO;

        if (bList == null || bList.isEmpty()) {
            return new BillRowAggregate(rows, 0,
                    total.toString(), paidTotal.toString(), adjTotal.toString(),
                    "0.00", "0.00", 0);
        }

        NumberFormat formatter = new DecimalFormat("#0.00");
        String invoiceNo = "";
        boolean nC = false;
        boolean newInvoice;
        boolean filterByRaCode = raCode != null
                && (raCode.trim().length() == 2 || raCode.trim().length() == 3);
        List<String> visibleBillingNos = new ArrayList<>();
        List<BillingRaLookupService.RaDataRequest> raRequests = new ArrayList<>();
        for (BillingClaimHeaderDto ch1Obj : bList) {
            if (isFilteredBySite(ch1Obj, multisitesEnabled, selectedSite, siteAccessPrivacy)) {
                continue;
            }
            visibleBillingNos.add(ch1Obj.getId());
            raRequests.add(new BillingRaLookupService.RaDataRequest(
                    ch1Obj.getId(),
                    raServiceDate(ch1Obj),
                    ch1Obj.providerOhipNo()));
        }
        Set<String> billingNosWithRaCode = filterByRaCode
                ? raLookupService.findBillingNosWithErrorCode(visibleBillingNos, raCode)
                : Collections.emptySet();
        Map<String, ArrayList<HashMap<String, String>>> raDataByKey =
                raLookupService.getRADataInternBatch(raRequests);

        for (BillingClaimHeaderDto ch1Obj : bList) {
            // multi-site filtering rules (mirrors legacy scriptlet)
            if (isFilteredBySite(ch1Obj, multisitesEnabled, selectedSite, siteAccessPrivacy)) {
                continue;
            }

            patientCount++;

            // ra-code error filter (e.g. "33", "V07")
            if (filterByRaCode) {
                if (!billingNosWithRaCode.contains(ch1Obj.getId())) {
                    continue;
                }
            }

            String ohipNo = ch1Obj.providerOhipNo();
            String raKey = BillingRaLookupService.RaDataRequest.key(ch1Obj.getId(), raServiceDate(ch1Obj), ohipNo);
            ArrayList<HashMap<String, String>> raList =
                    raDataByKey.getOrDefault(raKey, new ArrayList<HashMap<String, String>>());

            BigDecimal valueToAdd = new BigDecimal("0.00");
            try {
                valueToAdd = new BigDecimal(ch1Obj.getTotal()).setScale(2, RoundingMode.HALF_UP);
            } catch (NumberFormatException | NullPointerException e) {
                // Zero malformed totals so the page renders, but track the
                // count via getUnreadableTotalRowCount() so the JSP can
                // banner "N rows excluded" rather than silently understating
                // the grand total.
                unreadableTotalRowCount++;
                MiscUtils.getLogger().warn(
                        "BillingOnStatus: bill {} has unparseable total [{}]; excluded from grand total",
                        ch1Obj.getId(), ch1Obj.getTotal(), e);
            }
            total = total.add(valueToAdd);

            String amountPaid = "0.00";
            String errorCode = "";
            if ("-".equals(serviceCode) && raList.size() > 0) {
                io.github.carlos_emr.carlos.billings.ca.on.service.BillingRaLookupService.AmountPaidResult r =
                        raLookupService.getAmountPaidWithCount(raList);
                amountPaid = r.formattedTotal();
                unreadableTotalRowCount += r.unreadableCount();
                errorCode = raLookupService.getErrorCodes(raList);
            } else if (raList.size() > 0) {
                io.github.carlos_emr.carlos.billings.ca.on.service.BillingRaLookupService.AmountPaidResult r =
                        raLookupService.getAmountPaidWithCount(raList,
                                ch1Obj.getId(), ch1Obj.transactionId());
                amountPaid = r.formattedTotal();
                unreadableTotalRowCount += r.unreadableCount();
                errorCode = raLookupService.getErrorCodes(raList);
            }
            // 3rd-party billing pulls paid amount from the row directly.
            // Both this matcher and the BILLINGMATCHSTRING_3RDPARTY matcher
            // below must reference the same constant; an ||-chain that omits
            // any third-party code (e.g. IFH) silently skips 3rd-party
            // formatting on that pay program.
            if (ch1Obj.payProgram() != null
                    && ch1Obj.payProgram().matches(
                            io.github.carlos_emr.carlos.billings.ca.on.support.BillingOnConstants.BILLINGMATCHSTRING_3RDPARTY)) {
                amountPaid = ch1Obj.getPaid();
            }
            if (amountPaid == null || amountPaid.isEmpty() || "null".equals(amountPaid)) {
                amountPaid = "0.00";
            }

            BigDecimal bTemp;
            try {
                bTemp = new BigDecimal(amountPaid.trim()).setScale(2, RoundingMode.HALF_UP);
            } catch (NumberFormatException nfe) {
                // amountPaid comes from raLookupService.getAmountPaidWithCount,
                // which delegates to BillingMoney.format → toPlainString (no
                // currency symbol; e.g. "100.00"). A parse failure here means
                // the lookup returned malformed data. Surface as an
                // unreadable-row so the operator sees the same banner as
                // malformed totals.
                MiscUtils.getLogger().error(
                        "Could not parse amount paid for invoice {}; excluded from grand total",
                        ch1Obj.getId(), nfe);
                unreadableTotalRowCount++;
                bTemp = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            }
            // Reuse the already-parsed `valueToAdd` (which is zeroed for
            // unreadable rows above). Re-parsing ch1Obj.getTotal() here
            // would defeat the unreadableTotalRowCount exclusion and 500
            // the page on a malformed total.
            BigDecimal adj = valueToAdd.subtract(bTemp);
            paidTotal = paidTotal.add(bTemp);
            adjTotal = adjTotal.add(adj);

            int qty = ch1Obj.getNumItems();

            // Cache the equality once — the loop runs once per ch1 row and
            // we both branch on it AND advance invoiceNo when it flips.
            boolean sameInvoice = invoiceNo.equals(ch1Obj.getId());
            newInvoice = !sameInvoice;
            if (!sameInvoice) {
                invoiceNo = ch1Obj.getId();
                nC = !nC;
            }
            String rowClass = nC ? "success" : "";

            String settleDate = ch1Obj.settleDate();
            if (settleDate == null || !BillingONCHeader1.SETTLED.equals(ch1Obj.getStatus())) {
                settleDate = "N/A";
            } else if (settleDate.indexOf(' ') >= 0) {
                settleDate = settleDate.substring(0, settleDate.indexOf(' '));
            }

            String payProgram = ch1Obj.payProgram();
            // Same membership predicate as the matcher above — must use
            // the shared constant; an ||-chain that omits any third-party
            // code silently skips the formatting here while still sourcing
            // amountPaid from the third-party row above (asymmetric drift).
            boolean thirdParty = payProgram != null
                    && payProgram.matches(
                            io.github.carlos_emr.carlos.billings.ca.on.support.BillingOnConstants.BILLINGMATCHSTRING_3RDPARTY);

            String cash = formatter.format(ch1Obj.getCashTotal());
            String debit = formatter.format(ch1Obj.getDebitTotal());
            totalCash = totalCash.add(ch1Obj.getCashTotal());
            totalDebit = totalDebit.add(ch1Obj.getDebitTotal());

            String clinic = ch1Obj.getClinic();
            String clinicBgColor = "";
            String clinicShortName = "";
            if (clinic != null && !"null".equalsIgnoreCase(clinic)) {
                String bg = siteBgColor.get(clinic);
                if (bg != null) clinicBgColor = bg;
                String sn = siteShortName.get(clinic);
                if (sn != null) clinicShortName = sn;
            }

            rows.add(new BillingOnStatusViewModel.BillRow(
                    ch1Obj.getId(),
                    ch1Obj.billingDate(),
                    ch1Obj.demographicNo(),
                    ch1Obj.demographicName(),
                    ch1Obj.facilityNumber() != null ? ch1Obj.facilityNumber() : "",
                    ch1Obj.getStatus(),
                    settleDate,
                    nullToSpace(ch1Obj.transactionId()),
                    getStdCurr(ch1Obj.getTotal()),
                    amountPaid,
                    adj.toString(),
                    nullToSpace(ch1Obj.recordId()),
                    payProgram,
                    ch1Obj.getId(),
                    errorCode,
                    cash,
                    debit,
                    qty,
                    ch1Obj.getProviderName(),
                    clinic == null ? "" : clinic,
                    clinicBgColor,
                    clinicShortName,
                    newInvoice,
                    thirdParty,
                    rowClass));
        }

        return new BillRowAggregate(rows, patientCount,
                total.toString(), paidTotal.toString(), adjTotal.toString(),
                formatter.format(totalCash), formatter.format(totalDebit),
                unreadableTotalRowCount);
    }

    private static String firstNonNull(String primary, String fallback) {
        return primary != null ? primary : fallback;
    }

    private static String firstNonEmpty(String primary, String fallback) {
        return primary != null && !primary.isEmpty() ? primary : fallback;
    }

    private static boolean safeEquals(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    private static boolean isFilteredBySite(BillingClaimHeaderDto ch1Obj, boolean multisitesEnabled,
                                            String selectedSite, boolean siteAccessPrivacy) {
        if (multisitesEnabled && ch1Obj.getClinic() != null && selectedSite != null
                && !ch1Obj.getClinic().equals(selectedSite) && siteAccessPrivacy) {
            return true;
        }
        return multisitesEnabled && selectedSite != null
                && !selectedSite.equals(ch1Obj.getClinic());
    }

    private static String raServiceDate(BillingClaimHeaderDto ch1Obj) {
        return ch1Obj.billingDate() == null
                ? ""
                : ch1Obj.billingDate().replaceAll("\\D", "");
    }

    private static String nullToSpace(String s) {
        return s == null ? " " : s;
    }

    /** Mirrors {@code <%! String getStdCurr(...) %>} from the legacy JSP. */
    private static String getStdCurr(String s) {
        if (s == null) return null;
        if (s.indexOf('.') >= 0) {
            int dot = s.indexOf('.');
            int suffix = 3 - s.length() + dot;
            if (suffix < 0) suffix = 0;
            if (suffix > 2) suffix = 2;
            return s + "00".substring(0, suffix);
        }
        return s + ".00";
    }

    /** Mirrors {@code <%! String ch2StdCurrFromNoDot(...) %>} from the legacy JSP. */
    private static String ch2StdCurrFromNoDot(String s) {
        if (s == null) return null;
        if (s.indexOf('.') > 0) return s;
        if (s.length() > 2) {
            return s.substring(0, s.length() - 2) + "." + s.substring(s.length() - 2);
        }
        if (s.length() == 1) return "0.0" + s;
        return "0." + s;
    }
}
