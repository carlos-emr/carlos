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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.billings.ca.on.BillingMoney;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.GenerateRaSummaryViewModel;
import io.github.carlos_emr.carlos.commn.dao.BillingDao;
import io.github.carlos_emr.carlos.commn.dao.RaDetailDao;
import io.github.carlos_emr.carlos.commn.dao.RaHeaderDao;
import io.github.carlos_emr.carlos.commn.model.Billing;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.RaDetail;
import io.github.carlos_emr.carlos.commn.model.RaHeader;
import io.github.carlos_emr.carlos.util.ConversionUtils;
import io.github.carlos_emr.carlos.util.UtilDateUtilities;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * Shared assembler for {@code genRASummary.jsp} and
 * {@code genRASummaryDetail.jsp}, the OHIP RA payment-summary reports.
 * Both JSPs render the same data shape (RA detail rows classified by
 * Hospital / Local-clinic / Other, with cumulative totals); the only
 * differences in the legacy code were cosmetic. This assembler owns
 * the 5 inline {@code SpringUtils.getBean} lookups each JSP performed
 * (RaHeaderDao, RaDetailDao, ProviderDao, BillingDao + a duplicate
 * BillingDao lookup inside a {@code <%! %>} declaration).
 *
 * <p>The action layer persists computed totals after this assembler builds the
 * read-only model.</p>
 *
 * @since 2026-04-26
 */
@org.springframework.stereotype.Service
public class GenerateRaSummaryViewModelAssembler {

    private final RaHeaderDao raHeaderDao;
    private final RaDetailDao raDetailDao;
    private final ProviderDao providerDao;
    private final BillingDao billingDao;

    public GenerateRaSummaryViewModelAssembler(RaHeaderDao raHeaderDao,
                              RaDetailDao raDetailDao,
                              ProviderDao providerDao,
                              BillingDao billingDao) {
        this.raHeaderDao = raHeaderDao;
        this.raDetailDao = raDetailDao;
        this.providerDao = providerDao;
        this.billingDao = billingDao;
    }

    /**
     * Build the RA summary view model.
     *
     * @param request in-flight request — provides {@code rano}, {@code proNo}
     *                (provider OHIP number filter, may be empty/"all")
     * @return populated view model. Returns an empty stub when the RA header
     *         is missing or marked deleted.
     */
    public GenerateRaSummaryViewModel assemble(HttpServletRequest request, LoggedInInfo loggedInInfo) {
        String raNoStr = request.getParameter("rano");
        Integer raNo = parseInt(raNoStr);

        GenerateRaSummaryViewModel.Builder b = GenerateRaSummaryViewModel.builder().raNo(nullToEmpty(raNoStr));
        if (raNo == null) {
            if (raNoStr != null && !raNoStr.trim().isEmpty()) {
                MiscUtils.getLogger().warn("GenerateRaSummary: invalid RA number [{}]", // NOSONAR javasecurity:S5145 - sanitized with LogSafe
                        LogSafe.sanitize(raNoStr));
                b.raFileIncomplete(true)
                        .raFileWarning("Invalid RA number; no RA summary file was loaded.");
            }
            return b.build();
        }

        RaHeader rh = raHeaderDao.find(raNo);
        if (rh == null || "D".equals(rh.getStatus())) {
            return b.build();
        }

        // Selected provider OHIP from the dropdown — empty / "all" means show everyone.
        String proNo = request.getParameter("proNo");
        String filterPattern;
        if (proNo == null || proNo.isEmpty() || "all".equals(proNo)) {
            filterPattern = "%";
        } else {
            filterPattern = proNo + "%";
        }
        b.selectedProviderOhipNo(nullToEmpty(proNo));

        b.providerOptions(loadProviderOptions(raNo, proNo));

        // OB / colposcopy bill-numbers — used to flag rows for the OB column.
        Set<String> obBillingNos = new HashSet<>();
        for (Integer i : raDetailDao.search_raob(raNo)) {
            obBillingNos.add(i.toString());
        }
        Set<String> coBillingNos = new HashSet<>();
        for (Integer i : raDetailDao.search_racolposcopy(raNo)) {
            coBillingNos.add(i.toString());
        }

        // Provider-name lookup keyed on both ohipNo and "no_<providerNo>".
        // Mirrors the legacy `propProvierName` Properties bag used to render
        // demographic-doctor names alongside the bill row.
        Properties providerNames = buildProviderNameLookup();

        Totals totals = new Totals();
        List<GenerateRaSummaryViewModel.ReportRow> rows = new ArrayList<>();
        for (RaDetail rad : raDetailDao.search_rasummary_dt(raNo, filterPattern)) {
            rows.add(buildRow(rad, providerNames, obBillingNos, coBillingNos, totals));
        }

        BigDecimal localTotal = totals.clinic.add(totals.localHospital);
        b.rows(rows)
                .invoicedTotal(totals.invoiced.toPlainString())
                .paidTotal(totals.paid.toPlainString())
                .clinicPayTotal(totals.clinic.toPlainString())
                .hospitalPayTotal(totals.hospital.toPlainString())
                .obTotal(totals.ob.toPlainString())
                .raHeaderLocalTotal(localTotal.toPlainString())
                .otherPayTotal(totals.other.toPlainString())
                .coTotal(totals.colposcopy.toPlainString());

        return b.build();
    }

    private List<GenerateRaSummaryViewModel.ProviderOption> loadProviderOptions(Integer raNo, String selected) {
        List<GenerateRaSummaryViewModel.ProviderOption> options = new ArrayList<>();
        for (Object[] res : raDetailDao.search_raprovider(raNo)) {
            RaDetail rad = (RaDetail) res[0];
            Provider prov = (Provider) res[1];
            String ohipNo = nullToEmpty(rad.getProviderOhipNo());
            String displayName = nullToEmpty(prov.getLastName()) + "," + nullToEmpty(prov.getFirstName());
            boolean isSelected = ohipNo != null && !ohipNo.isEmpty() && ohipNo.equals(selected);
            options.add(new GenerateRaSummaryViewModel.ProviderOption(ohipNo, displayName, isSelected));
        }
        return options;
    }

    private Properties buildProviderNameLookup() {
        Properties out = new Properties();
        for (Provider p : providerDao.getActiveProviders()) {
            if (p.getOhipNo() == null || p.getOhipNo().isEmpty()) {
                continue;
            }
            String name = nullToEmpty(p.getLastName()) + "," + nullToEmpty(p.getFirstName());
            out.setProperty("no_" + p.getProviderNo(), name);
            out.setProperty(p.getOhipNo(), name);
        }
        return out;
    }

    private GenerateRaSummaryViewModel.ReportRow buildRow(RaDetail rad,
                                                     Properties providerNames,
                                                     Set<String> obBillingNos,
                                                     Set<String> coBillingNos,
                                                     Totals totals) {
        String account = String.valueOf(rad.getBillingNo());
        String demoHin = nullToEmpty(rad.getHin());
        String demoName = "";
        String demoDocName = "";
        String location = "";
        String localServiceDate = "";
        Billing b = billingDao.find(parseIntOrZero(account));
        if (b != null) {
            demoName = nullToEmpty(b.getDemographicName());
            String billHin = b.getHin();
            if (billHin == null || !billHin.startsWith(demoHin)) {
                demoHin = "";
                demoName = "";
            }
            location = nullToEmpty(b.getVisitType());
            localServiceDate = ConversionUtils.toDateString(b.getBillingDate()).replaceAll("-*", "");
            demoDocName = providerNames.getProperty("no_" + b.getProviderNo(), "");
        }

        String providerOhipNo = nullToEmpty(rad.getProviderOhipNo());
        String serviceCode = nullToEmpty(rad.getServiceCode());
        String serviceDate = nullToEmpty(rad.getServiceDate());
        String invoicedAmount = nullToEmpty(rad.getAmountClaim());
        String paidAmount = nullToEmpty(rad.getAmountPay());
        String errorCode = nullToEmpty(rad.getErrorCode());
        if (errorCode.isEmpty()) {
            errorCode = "**";
        }

        BigDecimal invoiced = BillingMoney.parseOptionalNonNegativeAmount(invoicedAmount, "invoicedAmount");
        // Strict on paid because totals.paid feeds RA-header persistence
        // (<xml_total> in RaHeader.content). amountOrZero would silently
        // zero-coalesce a malformed paid amount; the persisted reconciliation
        // would then drift below the source records. The strict variant
        // throws NumberFormatException on a malformed value so the action
        // sees the failure and skips the persister call.
        BigDecimal paid = BillingMoney.parseOptionalNonNegativeAmount(paidAmount, "paidAmount");
        totals.invoiced = totals.invoiced.add(invoiced);
        totals.paid = totals.paid.add(paid);

        boolean isOb = obBillingNos.contains(account);
        // CO is computed for parity with the legacy code but not exposed
        // in the rendered view; the legacy template never showed a CO column.
        boolean isCo = coBillingNos.contains(account);
        if (isCo) {
            totals.colposcopy = totals.colposcopy.add(paid);
        }
        String obAmount = isOb ? paidAmount : "N/A";
        if (isOb) {
            totals.ob = totals.ob.add(paid);
        }

        GenerateRaSummaryViewModel.Category category;
        if ("02".equals(location)) {
            category = GenerateRaSummaryViewModel.Category.HOSPITAL;
            totals.hospital = totals.hospital.add(paid);
            if (demoHin.length() > 1 && serviceDate.equals(localServiceDate)) {
                totals.localHospital = totals.localHospital.add(paid);
            }
        } else if ("00".equals(location) && wasBilledLocal(account, providerOhipNo, serviceDate, serviceCode)) {
            category = GenerateRaSummaryViewModel.Category.LOCAL_CLINIC;
            totals.clinic = totals.clinic.add(paid);
        } else {
            category = GenerateRaSummaryViewModel.Category.OTHER;
            totals.other = totals.other.add(paid);
        }

        return new GenerateRaSummaryViewModel.ReportRow(
                account, demoDocName, demoName, demoHin, serviceDate,
                serviceCode, invoicedAmount, paidAmount, obAmount, errorCode, category);
    }

    private boolean wasBilledLocal(String account, String provider, String billingDate, String code) {
        Date date = UtilDateUtilities.getDateFromString(billingDate, "yyyyMMdd");
        if (date == null) {
            return false;
        }
        return billingDao.findBillingsByManyThings(
                ConversionUtils.fromIntString(account), date, provider, code).size() >= 1;
    }

    private static Integer parseInt(String s) {
        if (s == null) return null;
        try { return Integer.valueOf(s); } catch (NumberFormatException e) { return null; }
    }

    private static int parseIntOrZero(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException | NullPointerException e) {
            MiscUtils.getLogger().warn("GenerateRaSummary: invalid integer [{}]; using 0", // NOSONAR javasecurity:S5145 - sanitized with LogSafe
                    LogSafe.sanitize(s), e);
            return 0;
        }
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }

    /** Per-category accumulators used while iterating RaDetail rows. */
    private static class Totals {
        BigDecimal invoiced = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal paid = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal clinic = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal hospital = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal localHospital = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal other = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal ob = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal colposcopy = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }
}
