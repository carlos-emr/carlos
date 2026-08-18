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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.SxmlMisc;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.billings.ca.on.service.RaDescriptionFileParser;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.GenerateRaDescriptionViewModel;
import io.github.carlos_emr.carlos.commn.dao.BillingONPremiumDao;
import io.github.carlos_emr.carlos.commn.dao.RaHeaderDao;
import io.github.carlos_emr.carlos.commn.model.BillingONPremium;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.RaHeader;
import io.github.carlos_emr.carlos.util.DateUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * Assembles {@link GenerateRaDescriptionViewModel} for {@code genRADesc.jsp}, the OHIP RA
 * (Remittance Advice) reconciliation report. Owns the 3 inline
 * {@code SpringUtils.getBean} lookups the JSP body used to perform
 * (RaHeaderDao, BillingONPremiumDao, ProviderDao) plus the RA-file parsing
 * projection.
 *
 * <p>The action layer invokes the RA header/premium persister before calling
 * this assembler. This class is read-only.</p>
 *
 * @since 2026-04-26
 */
@org.springframework.stereotype.Service
public class GenerateRaDescriptionViewModelAssembler {

    private final RaHeaderDao raHeaderDao;
    private final BillingONPremiumDao billingONPremiumDao;
    private final ProviderDao providerDao;
    private final RaDescriptionFileParser raDescriptionFileParser;

    public GenerateRaDescriptionViewModelAssembler(RaHeaderDao raHeaderDao,
                           BillingONPremiumDao billingONPremiumDao,
                           ProviderDao providerDao,
                           RaDescriptionFileParser raDescriptionFileParser) {
        this.raHeaderDao = raHeaderDao;
        this.billingONPremiumDao = billingONPremiumDao;
        this.providerDao = providerDao;
        this.raDescriptionFileParser = raDescriptionFileParser;
    }

    /**
     * Build the RA reconciliation view model.
     *
     * @param request in-flight request — supplies the {@code rano} parameter
     *                and the locale for date formatting
     * @param loggedInInfo session principal, retained for action/assembler
     *                     signature parity
     * @return populated view model. Returns an empty stub when the RA header
     *         is missing or marked deleted ({@code status="D"}); the JSP
     *         renders a near-empty page in that case.
     */
    public GenerateRaDescriptionViewModel assemble(HttpServletRequest request, LoggedInInfo loggedInInfo) {
        String raNoStr = request.getParameter("rano");
        Integer raNo = parseInt(raNoStr);

        GenerateRaDescriptionViewModel.Builder b = GenerateRaDescriptionViewModel.builder().raNo(nullToEmpty(raNoStr));
        if (raNo == null) {
            if (raNoStr != null && !raNoStr.isBlank()) {
                MiscUtils.getLogger().warn("GenerateRaDescription: invalid RA number [{}]", // NOSONAR javasecurity:S5145 - sanitized with LogSafe
                        LogSafe.sanitize(raNoStr));
                b.raFileIncomplete(true)
                        .raFileWarning("Invalid RA number; no RA description file was loaded.");
            }
            return b.build();
        }

        RaHeader rh = raHeaderDao.find(raNo);
        if (rh == null || "D".equals(rh.getStatus())) {
            return b.build();
        }

        RaDescriptionFileParser.ParsedFile parsed = raDescriptionFileParser.parse(rh.getFilename());
        if (!parsed.isCompleteForHeaderMerge()) {
            b.raFileIncomplete(true)
                    .raFileWarning(parseWarning(parsed.parseFailureReason()));
        }

        // Existing RaHeader content carries non-file totals populated by
        // upstream pipelines; expose them alongside the parsed file rows.
        String existingContent = nullToEmpty(rh.getContent());
        String localTotal = nullToEmpty(SxmlMisc.getXmlContent(existingContent, "<xml_local>", "</xml_local>"));
        String otherTotal = nullToEmpty(SxmlMisc.getXmlContent(existingContent, "<xml_other_total>", "</xml_other_total>"));
        String obTotal = nullToEmpty(SxmlMisc.getXmlContent(existingContent, "<xml_ob_total>", "</xml_ob_total>"));
        String coTotal = nullToEmpty(SxmlMisc.getXmlContent(existingContent, "<xml_co_total>", "</xml_co_total>"));

        b.localTotal(localTotal)
                .otherTotal(otherTotal)
                .obTotal(obTotal)
                .coTotal(coTotal);
        if (parsed.isCompleteForHeaderMerge()) {
            b.chequeTotal(parsed.cheque())
                    .balanceForwardRow(parsed.balanceForwardRow())
                    .transactionRows(parsed.transactionRows())
                    .messageTxt(parsed.messageTxt());
        }

        // Practitioner premiums: load each row's OHIP-mapped provider dropdown
        // options. Lazy population happens in the action's persister.
        b.premiumRows(loadPremiumRows(raNo, request.getLocale()));

        return b.build();
    }

    private static String parseWarning(RaDescriptionFileParser.ParseFailureReason reason) {
        return switch (reason) {
            case MISSING_FILENAME -> "RA description file is missing.";
            case SECURITY_REJECTED -> "RA description file path was rejected.";
            case IO_ERROR -> "RA description file could not be read.";
            case MALFORMED_RECORD -> "RA description file contains a malformed H6/H7 record; totals were not merged.";
            case INCOMPLETE_HEADER -> "RA description file is incomplete; H1 header totals could not be parsed.";
            case NONE -> "RA description file could not be fully parsed.";
        };
    }

    private List<GenerateRaDescriptionViewModel.PremiumRow> loadPremiumRows(Integer raNo, Locale locale) {
        List<GenerateRaDescriptionViewModel.PremiumRow> rows = new ArrayList<>();
        for (BillingONPremium premium : billingONPremiumDao.getRAPremiumsByRaHeaderNo(raNo)) {
            List<Provider> providers = providerDao.getBillableProvidersByOHIPNo(premium.getProviderOHIPNo());
            if (providers == null || providers.isEmpty()) {
                // Do not render an unbound dropdown row; if no current provider
                // matches the premium's OHIP number, the operator needs the
                // upstream provider mapping fixed rather than a blank select.
                continue;
            }
            List<GenerateRaDescriptionViewModel.ProviderOption> options = new ArrayList<>();
            String premiumProviderNo = premium.getProviderNo();
            for (Provider p : providers) {
                boolean selected = premiumProviderNo != null
                        && premiumProviderNo.equals(p.getProviderNo());
                options.add(new GenerateRaDescriptionViewModel.ProviderOption(
                        nullToEmpty(p.getProviderNo()),
                        nullToEmpty(p.getFormattedName()),
                        selected));
            }
            rows.add(new GenerateRaDescriptionViewModel.PremiumRow(
                    premium.getId(),
                    nullToEmpty(premium.getProviderOHIPNo()),
                    nullToEmpty(premium.getAmountPay()),
                    DateUtils.formatDate(premium.getPayDate(), locale),
                    Boolean.TRUE.equals(premium.getStatus()),
                    options));
        }
        return rows;
    }

    private static Integer parseInt(String s) {
        if (s == null) return null;
        try {
            return Integer.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }
}
