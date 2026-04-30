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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.carlos_emr.CarlosProperties;
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
import io.github.carlos_emr.carlos.utility.SafeEncode;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingStatusQueryService;
import io.github.carlos_emr.carlos.billings.ca.on.web.ViewBillingOnStatus2Action;

/**
 * Assembles {@link BillingOnStatusViewModel} for {@code billingONStatus.jsp}.
 *
 * <p>Extracted from {@link ViewBillingOnStatus2Action} so the action stays a
 * thin gate (security check + assembler invocation) and the parameter-echo +
 * default-resolution + DB-fan-out logic is testable in isolation. Mirrors the
 * {@link BillingOnReviewViewModelAssembler} / {@link BillingShortcutPg1ViewModelAssembler}
 * shape: production no-arg ctor + package-private mock-injection ctor +
 * {@link #assemble(HttpServletRequest, LoggedInInfo)}.</p>
 *
 * @since 2026-04-25
 */
@org.springframework.stereotype.Service
public class BillingOnStatusViewModelAssembler {

    private final SecurityInfoManager securityInfoManager;
    private final BillingOnLookupService lookupService;
    private final BillingStatusQueryService statusPrep;
    private final BillingOnErrorReportService errorRepImpl;
    private final SiteDao siteDao;
    private final BillingRaLookupService raLookupService;

    public BillingOnStatusViewModelAssembler(SecurityInfoManager securityInfoManager,
                                 BillingOnLookupService lookupService,
                                 BillingStatusQueryService statusPrep,
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
        List<String> pList = teamBillingOnly
                ? lookupService.getCurTeamProviderStr(sessionUser)
                : lookupService.getCurProviderStr();
        if (pList == null) {
            pList = Collections.emptyList();
        }
        List<BillingOnStatusViewModel.ProviderOption> providers = new ArrayList<>(pList.size());
        for (String entry : pList) {
            String[] parts = entry.split("\\|", -1);
            String pNo = parts.length > 0 ? parts[0] : "";
            String last = parts.length > 1 ? parts[1] : "";
            String first = parts.length > 2 ? parts[2] : "";
            String ohip = parts.length > 3 ? parts[3] : "";
            providers.add(new BillingOnStatusViewModel.ProviderOption(
                    pNo, last, first, ohip));
        }

        // ---- multisite siteBgColor / siteShortName lookup maps + per-site
        //      provider option lists ----
        Map<String, String> siteBgColorMap = new HashMap<>();
        Map<String, String> siteShortNameMap = new HashMap<>();
        List<BillingMultisiteContext.MultisiteSite> multisiteSites = new ArrayList<>();
        Map<String, String> multisiteProviderHtml = new LinkedHashMap<>();
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
                for (String entry : pList) {
                    int sep = entry.indexOf('|');
                    allowedProviders.add(sep >= 0 ? entry.substring(0, sep) : entry);
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
                    StringBuilder html = new StringBuilder();
                    for (BillingMultisiteContext.MultisiteProvider mp : providerOpts) {
                        html.append("<option value='")
                                .append(SafeEncode.forHtmlAttribute(mp.providerNo()))
                                .append("'>")
                                .append(SafeEncode.forHtml(mp.lastName()))
                                .append(", ")
                                .append(SafeEncode.forHtml(mp.firstName()))
                                .append("</option>");
                    }
                    multisiteProviderHtml.put(site.getName(), html.toString());
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
            // deepcode ignore SqlInjection: BillingStatusQueryService delegates to
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
                .multisiteProviderHtml(multisiteProviderHtml)
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
            List<String> pList, String providerNo, String startDate, String endDate,
            String filename) {
        List<String> aLProviders;
        if (providerNo == null || providerNo.isEmpty()) {
            aLProviders = new ArrayList<>(pList);
        } else {
            aLProviders = new ArrayList<>();
            aLProviders.add(providerNo);
        }
        List<BillingOnStatusViewModel.RejectedBillRow> rows = new ArrayList<>();
        for (String entry : aLProviders) {
            String[] provInfo = entry.split("\\|", -1);
            String currentProvider = provInfo.length > 0 ? provInfo[0].trim() : "";
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
                try {
                    formattedFee = String.valueOf(Integer.parseInt(bObj.getFee()));
                } catch (NumberFormatException nfe) {
                    formattedFee = "N/A";
                }
                String stdCurr = ch2StdCurrFromNoDot(formattedFee);
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
        double totalCash = 0d;
        double totalDebit = 0d;

        if (bList == null || bList.isEmpty()) {
            return new BillRowAggregate(rows, 0,
                    total.toString(), paidTotal.toString(), adjTotal.toString(),
                    "0.00", "0.00", 0);
        }

        NumberFormat formatter = new DecimalFormat("#0.00");
        String invoiceNo = "";
        boolean nC = false;
        boolean newInvoice;

        for (BillingClaimHeaderDto ch1Obj : bList) {
            // multi-site filtering rules (mirrors legacy scriptlet)
            if (multisitesEnabled && ch1Obj.getClinic() != null && selectedSite != null
                    && !ch1Obj.getClinic().equals(selectedSite) && siteAccessPrivacy) {
                continue;
            }
            if (multisitesEnabled && selectedSite != null
                    && !selectedSite.equals(ch1Obj.getClinic())) {
                continue;
            }

            patientCount++;

            // ra-code error filter (e.g. "33", "V07")
            if (raCode != null
                    && (raCode.trim().length() == 2 || raCode.trim().length() == 3)) {
                if (!raLookupService.isErrorCode(ch1Obj.getId(), raCode)) {
                    continue;
                }
            }

            String ohipNo = ch1Obj.getProvider_ohip_no();
            ArrayList<HashMap<String, String>> raList = raLookupService.getRADataIntern(
                    ch1Obj.getId(),
                    ch1Obj.getBilling_date() == null
                            ? ""
                            : ch1Obj.getBilling_date().replaceAll("\\D", ""),
                    ohipNo);

            BigDecimal valueToAdd = new BigDecimal("0.00");
            try {
                valueToAdd = new BigDecimal(ch1Obj.getTotal()).setScale(2, RoundingMode.HALF_UP);
            } catch (NumberFormatException | NullPointerException e) {
                // Pre-fix this caught Exception with `ignored` and zeroed
                // silently — the running grand-total understated by every
                // malformed bill row. Continue with zero so the page still
                // renders, but log so ops can spot drift, narrow to the
                // two parse-failure modes (NFE on bad numeric, NPE on null
                // total), AND track a count surfaced via
                // {@link BillingOnStatusViewModel#getUnreadableTotalRowCount()}
                // so the JSP can render a "N rows excluded" banner.
                unreadableTotalRowCount++;
                MiscUtils.getLogger().warn(
                        "BillingOnStatus: bill {} has unparseable total [{}]; excluded from grand total",
                        ch1Obj.getId(), ch1Obj.getTotal());
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
                                ch1Obj.getId(), ch1Obj.getTransc_id());
                amountPaid = r.formattedTotal();
                unreadableTotalRowCount += r.unreadableCount();
                errorCode = raLookupService.getErrorCodes(raList);
            }
            // 3rd-party billing pulls paid amount from the row directly.
            // Use the shared constant so this and the second matcher below
            // (around line 561) can't drift apart silently — pre-fix the
            // ||-chain matcher omitted IFH, so an IFH bill skipped the
            // 3rd-party formatting path while this one applied it.
            if (ch1Obj.getPay_program() != null
                    && ch1Obj.getPay_program().matches(
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
                // amountPaid comes from raLookupService.getAmountPaid which
                // formats to "$X.XX" — a parse failure here means the lookup
                // returned malformed data. Surface as an unreadable-row so
                // the operator sees the same banner as malformed totals.
                MiscUtils.getLogger().error(
                        "Could not parse amount paid for invoice {}; excluded from grand total",
                        ch1Obj.getId(), nfe);
                unreadableTotalRowCount++;
                bTemp = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            }
            // Reuse the already-parsed `valueToAdd` (which is zeroed for
            // unreadable rows above). Pre-fix this re-parsed ch1Obj.getTotal()
            // here and rethrew on NFE, defeating the unreadableTotalRowCount
            // mechanism — every malformed-total row would 500 the status page
            // even though it was already "excluded from grand total" upstream.
            BigDecimal adj = valueToAdd.subtract(bTemp);
            paidTotal = paidTotal.add(bTemp);
            adjTotal = adjTotal.add(adj);

            int qty = ch1Obj.getNumItems();

            if (invoiceNo.equals(ch1Obj.getId())) {
                newInvoice = false;
            } else {
                newInvoice = true;
            }
            if (!invoiceNo.equals(ch1Obj.getId())) {
                invoiceNo = ch1Obj.getId();
                nC = !nC;
            }
            String rowClass = nC ? "success" : "";

            String settleDate = ch1Obj.getSettle_date();
            if (settleDate == null || !BillingONCHeader1.SETTLED.equals(ch1Obj.getStatus())) {
                settleDate = "N/A";
            } else if (settleDate.indexOf(' ') >= 0) {
                settleDate = settleDate.substring(0, settleDate.indexOf(' '));
            }

            String payProgram = ch1Obj.getPay_program();
            // Same membership predicate as the matcher above — use the shared
            // constant so the two checks can't diverge. Pre-fix this chain
            // omitted IFH that the regex above includes; an IFH bill silently
            // skipped the third-party formatting path here while still being
            // treated as third-party for amountPaid sourcing.
            boolean thirdParty = payProgram != null
                    && payProgram.matches(
                            io.github.carlos_emr.carlos.billings.ca.on.support.BillingOnConstants.BILLINGMATCHSTRING_3RDPARTY);

            String cash = formatter.format(ch1Obj.getCashTotal());
            String debit = formatter.format(ch1Obj.getDebitTotal());
            totalCash += ch1Obj.getCashTotal();
            totalDebit += ch1Obj.getDebitTotal();

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
                    ch1Obj.getBilling_date(),
                    ch1Obj.getDemographic_no(),
                    ch1Obj.getDemographic_name(),
                    ch1Obj.getFacilty_num() != null ? ch1Obj.getFacilty_num() : "",
                    ch1Obj.getStatus(),
                    settleDate,
                    nullToSpace(ch1Obj.getTransc_id()),
                    getStdCurr(ch1Obj.getTotal()),
                    amountPaid,
                    adj.toString(),
                    nullToSpace(ch1Obj.getRec_id()),
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
