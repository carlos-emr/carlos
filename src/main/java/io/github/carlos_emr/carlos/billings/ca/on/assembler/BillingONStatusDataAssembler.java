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

import jakarta.servlet.http.HttpServletRequest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.billings.ca.on.data.BillingClaimHeader1Data;
import io.github.carlos_emr.carlos.billings.ca.on.data.BillingErrorRepData;
import io.github.carlos_emr.carlos.billings.ca.on.data.BillingMultisiteContext;
import io.github.carlos_emr.carlos.billings.ca.on.data.BillingONStatusViewModel;
import io.github.carlos_emr.carlos.billings.ca.on.data.BillingProviderData;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingONErrorReportService;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingONLookupService;
import io.github.carlos_emr.carlos.billings.ca.on.data.RAData;
import io.github.carlos_emr.carlos.commn.IsPropertiesOn;
import io.github.carlos_emr.carlos.commn.dao.SiteDao;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.Site;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.util.DateUtils;
import io.github.carlos_emr.carlos.util.LabelValueBean;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.billings.ca.on.pageUtil.BillingStatusPrep;
import io.github.carlos_emr.carlos.billings.ca.on.web.ViewBillingONStatus2Action;

/**
 * Assembles {@link BillingONStatusViewModel} for {@code billingONStatus.jsp}.
 *
 * <p>Extracted from {@link ViewBillingONStatus2Action} so the action stays a
 * thin gate (security check + assembler invocation) and the parameter-echo +
 * default-resolution + DB-fan-out logic is testable in isolation. Mirrors the
 * {@link BillingONReviewDataAssembler} / {@link BillingShortcutPg1DataAssembler}
 * shape: production no-arg ctor + package-private mock-injection ctor +
 * {@link #assemble(HttpServletRequest, LoggedInInfo)}.</p>
 *
 * @since 2026-04-25
 */
public final class BillingONStatusDataAssembler {

    private final SecurityInfoManager securityInfoManager;

    /**
     * Production constructor used by Struts; resolves dependencies from the
     * Spring context via {@link SpringUtils#getBean}. Tests use the
     * package-private constructor below to inject mocks directly.
     */
    public BillingONStatusDataAssembler() {
        this(SpringUtils.getBean(SecurityInfoManager.class));
    }

    BillingONStatusDataAssembler(SecurityInfoManager securityInfoManager) {
        this.securityInfoManager = securityInfoManager;
    }

    /**
     * Builds the status-page view model from request parameters and the
     * logged-in user's privilege flags. Pure read; no side effects on
     * persisted state.
     */
    public BillingONStatusViewModel assemble(HttpServletRequest request, LoggedInInfo loggedInInfo) {
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
                : BillingONStatusViewModel.DEFAULT_BILL_TYPES.toArray(new String[0]);

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

        String serviceCode = request.getParameter("serviceCode");
        if (serviceCode == null || serviceCode.isEmpty()) {
            serviceCode = "%";
        }

        // Legacy "any billing form" sentinel is three dashes; a single "-" is a
        // real value in some installations and would mis-filter the search.
        String billingForm = firstNonNull(request.getParameter("billing_form"), "---");
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
        BillingONLookupService pageUtil = new BillingONLookupService();
        List<String> pList = teamBillingOnly
                ? pageUtil.getCurTeamProviderStr(sessionUser)
                : pageUtil.getCurProviderStr();
        if (pList == null) {
            pList = Collections.emptyList();
        }
        List<BillingONStatusViewModel.ProviderOption> providers = new ArrayList<>(pList.size());
        for (String entry : pList) {
            String[] parts = entry.split("\\|", -1);
            String pNo = parts.length > 0 ? parts[0] : "";
            String last = parts.length > 1 ? parts[1] : "";
            String first = parts.length > 2 ? parts[2] : "";
            String ohip = parts.length > 3 ? parts[3] : "";
            providers.add(new BillingONStatusViewModel.ProviderOption(
                    pNo, last, first, ohip));
        }

        // ---- multisite siteBgColor / siteShortName lookup maps + per-site
        //      provider option lists ----
        Map<String, String> siteBgColorMap = new HashMap<>();
        Map<String, String> siteShortNameMap = new HashMap<>();
        List<BillingMultisiteContext.MultisiteSite> multisiteSites = new ArrayList<>();
        Map<String, String> multisiteProviderHtml = new LinkedHashMap<>();
        if (multisitesEnabled) {
            SiteDao siteDao = lookupSiteDao(request);
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
                                .append(escapeHtmlAttr(mp.providerNo()))
                                .append("'>")
                                .append(escapeHtml(mp.lastName()))
                                .append(", ")
                                .append(escapeHtml(mp.firstName()))
                                .append("</option>");
                    }
                    multisiteProviderHtml.put(site.getName(), html.toString());
                }
            }
        }

        // ---- billing forms list ----
        BillingStatusPrep statusPrep = new BillingStatusPrep();
        List<LabelValueBean> formList = statusPrep.listBillingForms();
        List<BillingONStatusViewModel.BillingFormOption> billingForms = new ArrayList<>();
        if (formList != null) {
            for (LabelValueBean lv : formList) {
                billingForms.add(new BillingONStatusViewModel.BillingFormOption(
                        lv.getLabel(), lv.getValue()));
            }
        }

        // ---- visit-location list ----
        List<BillingONStatusViewModel.VisitLocationOption> visitLocations = new ArrayList<>();
        @SuppressWarnings("rawtypes")
        List facilityNums = pageUtil.getFacilty_num();
        if (facilityNums != null) {
            for (int i = 0; i < facilityNums.size() - 1; i += 2) {
                String code = (String) facilityNums.get(i);
                String label = (String) facilityNums.get(i + 1);
                visitLocations.add(new BillingONStatusViewModel.VisitLocationOption(code, label));
            }
        }

        // ---- bill list (rejected vs sorted) ----
        List<BillingClaimHeader1Data> bList;
        if ((serviceCode == null || billingForm == null)
                && dx.length() < 2 && visitType.length() < 2) {
            // deepcode ignore SqlInjection: BillingStatusPrep delegates to
            // JdbcBillingReviewImpl which uses JPA criteria queries (parameterized)
            bList = search
                    ? statusPrep.getBills(billTypes, statusType, providerNo, startDate, endDate,
                            demoNo, visitLocation, paymentStartDate, paymentEndDate)
                    : new ArrayList<BillingClaimHeader1Data>();
            serviceCode = "%";
        } else {
            String effectiveServiceCode = (serviceCode == null || serviceCode.length() < 2)
                    ? "%" : serviceCode;
            bList = search
                    ? statusPrep.getBillsWithSorting(billTypes, statusType, providerNo, startDate,
                            endDate, demoNo, effectiveServiceCode, dx, visitType, billingForm,
                            visitLocation, sortName, sortOrder, paymentStartDate, paymentEndDate,
                            claimNo)
                    : new ArrayList<BillingClaimHeader1Data>();
            serviceCode = effectiveServiceCode;
        }

        // ---- rejected-bill rows ("_") ----
        List<BillingONStatusViewModel.RejectedBillRow> rejectedRows = "_".equals(statusType)
                ? buildRejectedRows(pList, providerNo, startDate, endDate, filename)
                : Collections.<BillingONStatusViewModel.RejectedBillRow>emptyList();

        // ---- normal bill rows + aggregate totals ----
        BillRowAggregate agg = buildBillRows(bList, multisitesEnabled, selectedSite,
                siteAccessPrivacy, raCode, serviceCode, siteBgColorMap, siteShortNameMap);

        // ---- request param echoes ----
        Map<String, String> echoes = new HashMap<>();
        for (String name : new String[]{"site", "providerview"}) {
            String v = request.getParameter(name);
            if (v != null) echoes.put(name, v);
        }

        return BillingONStatusViewModel.builder()
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
                .totalBilled(agg.totalBilled())
                .totalPaid(agg.totalPaid())
                .totalAdjustments(agg.totalAdjustments())
                .totalCash(agg.totalCash())
                .totalDebit(agg.totalDebit())
                .requestParamEchoes(echoes)
                .build();
    }

    private SiteDao lookupSiteDao(HttpServletRequest request) {
        try {
            WebApplicationContext ctx = WebApplicationContextUtils
                    .getWebApplicationContext(request.getServletContext());
            return ctx == null ? SpringUtils.getBean(SiteDao.class) : ctx.getBean(SiteDao.class);
        } catch (RuntimeException ex) {
            MiscUtils.getLogger().error("Failed to resolve SiteDao", ex);
            return null;
        }
    }

    private List<BillingONStatusViewModel.RejectedBillRow> buildRejectedRows(
            List<String> pList, String providerNo, String startDate, String endDate,
            String filename) {
        List<String> aLProviders;
        if (providerNo == null || providerNo.isEmpty()) {
            aLProviders = new ArrayList<>(pList);
        } else {
            aLProviders = new ArrayList<>();
            aLProviders.add(providerNo);
        }
        List<BillingONStatusViewModel.RejectedBillRow> rows = new ArrayList<>();
        BillingONLookupService pageUtil = new BillingONLookupService();
        BillingONErrorReportService errorRepImpl = new BillingONErrorReportService();
        for (String entry : aLProviders) {
            String[] provInfo = entry.split("\\|", -1);
            String currentProvider = provInfo.length > 0 ? provInfo[0].trim() : "";
            @SuppressWarnings("rawtypes")
            List lPat;
            if ("all".equals(currentProvider)) {
                List<BillingProviderData> providerObjs = pageUtil.getProviderObjList(currentProvider);
                lPat = errorRepImpl.getErrorRecords(providerObjs, startDate, endDate, filename);
            } else {
                BillingProviderData providerObj = pageUtil.getProviderObj(currentProvider);
                lPat = errorRepImpl.getErrorRecords(providerObj, startDate, endDate, filename);
            }
            if (lPat == null) continue;
            boolean nC = false;
            String invoiceNo = "";
            for (Object raw : lPat) {
                BillingErrorRepData bObj = (BillingErrorRepData) raw;
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
                boolean checked = !"N".equals(bObj.getStatus());
                rows.add(new BillingONStatusViewModel.RejectedBillRow(
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
            List<BillingONStatusViewModel.BillRow> rows,
            int patientCount,
            String totalBilled,
            String totalPaid,
            String totalAdjustments,
            String totalCash,
            String totalDebit) { }

    private BillRowAggregate buildBillRows(
            List<BillingClaimHeader1Data> bList, boolean multisitesEnabled, String selectedSite,
            boolean siteAccessPrivacy, String raCode, String serviceCode,
            Map<String, String> siteBgColor, Map<String, String> siteShortName) {
        List<BillingONStatusViewModel.BillRow> rows = new ArrayList<>();
        int patientCount = 0;
        BigDecimal total = new BigDecimal("0").setScale(2, RoundingMode.HALF_UP);
        BigDecimal paidTotal = new BigDecimal("0").setScale(2, RoundingMode.HALF_UP);
        BigDecimal adjTotal = new BigDecimal("0").setScale(2, RoundingMode.HALF_UP);
        double totalCash = 0d;
        double totalDebit = 0d;

        if (bList == null || bList.isEmpty()) {
            return new BillRowAggregate(rows, 0,
                    total.toString(), paidTotal.toString(), adjTotal.toString(),
                    "0.00", "0.00");
        }

        RAData raData = new RAData();
        NumberFormat formatter = new DecimalFormat("#0.00");
        String invoiceNo = "";
        boolean nC = false;
        boolean newInvoice;

        for (BillingClaimHeader1Data ch1Obj : bList) {
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
                if (!raData.isErrorCode(ch1Obj.getId(), raCode)) {
                    continue;
                }
            }

            String ohipNo = ch1Obj.getProvider_ohip_no();
            ArrayList<Hashtable<String, String>> raList = raData.getRADataIntern(
                    ch1Obj.getId(),
                    ch1Obj.getBilling_date() == null
                            ? ""
                            : ch1Obj.getBilling_date().replaceAll("\\D", ""),
                    ohipNo);

            BigDecimal valueToAdd = new BigDecimal("0.00");
            try {
                valueToAdd = new BigDecimal(ch1Obj.getTotal()).setScale(2, RoundingMode.HALF_UP);
            } catch (Exception ignored) {
                // Mirror legacy: keep going with zero on parse failure.
            }
            total = total.add(valueToAdd);

            String amountPaid = "0.00";
            String errorCode = "";
            if ("-".equals(serviceCode) && raList.size() > 0) {
                amountPaid = raData.getAmountPaid(raList);
                errorCode = raData.getErrorCodes(raList);
            } else if (raList.size() > 0) {
                amountPaid = raData.getAmountPaid(raList,
                        ch1Obj.getId(), ch1Obj.getTransc_id());
                errorCode = raData.getErrorCodes(raList);
            }
            // 3rd-party billing pulls paid amount from the row directly
            if (ch1Obj.getPay_program() != null
                    && ch1Obj.getPay_program().matches("PAT|OCF|ODS|CPP|STD|IFH")) {
                amountPaid = ch1Obj.getPaid();
            }
            if (amountPaid == null || amountPaid.isEmpty() || "null".equals(amountPaid)) {
                amountPaid = "0.00";
            }

            BigDecimal bTemp;
            BigDecimal adj;
            try {
                bTemp = new BigDecimal(amountPaid.trim()).setScale(2, RoundingMode.HALF_UP);
                adj = new BigDecimal(ch1Obj.getTotal()).setScale(2, RoundingMode.HALF_UP);
            } catch (NumberFormatException nfe) {
                MiscUtils.getLogger().error(
                        "Could not parse amount paid for invoice " + ch1Obj.getId(), nfe);
                throw nfe;
            }
            paidTotal = paidTotal.add(bTemp);
            adj = adj.subtract(bTemp);
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
            if (settleDate == null || !"S".equals(ch1Obj.getStatus())) {
                settleDate = "N/A";
            } else if (settleDate.indexOf(' ') >= 0) {
                settleDate = settleDate.substring(0, settleDate.indexOf(' '));
            }

            String payProgram = ch1Obj.getPay_program();
            boolean thirdParty = "PAT".equals(payProgram) || "OCF".equals(payProgram)
                    || "ODS".equals(payProgram) || "CPP".equals(payProgram)
                    || "STD".equals(payProgram);

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

            rows.add(new BillingONStatusViewModel.BillRow(
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
                formatter.format(totalCash), formatter.format(totalDebit));
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

    private static String escapeHtml(String s) {
        return org.owasp.encoder.Encode.forHtml(s == null ? "" : s);
    }

    private static String escapeHtmlAttr(String s) {
        return org.owasp.encoder.Encode.forHtmlAttribute(s == null ? "" : s);
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
