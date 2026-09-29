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
package io.github.carlos_emr.carlos.billings.ca.on.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException;
import io.github.carlos_emr.carlos.commn.dao.RaDetailDao;
import io.github.carlos_emr.carlos.commn.dao.RaHeaderDao;
import io.github.carlos_emr.carlos.commn.model.RaDetail;
import io.github.carlos_emr.carlos.commn.model.RaHeader;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.MiscUtils;
/**
 * Mutation service for the two RA settlement popups —
 * {@code onGenRAsettle.jsp} (settle all non-error bills, mark RA header
 * as "S") and {@code onGenRAsettle35.jsp} (the I2/35 error variant with
 * special-case Q-code allow-list).
 *
 * <p>Owns the 3 inline {@code SpringUtils.getBean} lookups each JSP used
 * to perform (RaHeaderDao, BillingDao, RaDetailDao) plus the
 * {@code BillingRaReportService.updateBillingStatus} mutation. Both pages emit a
 * single client-side {@code self.close()} script after the mutation —
 * no view-model state is exposed; the action layer just calls
 * {@link #settle} and forwards to a tiny rendering JSP.</p>
 *
 * @since 2026-04-26
 */
@org.springframework.stereotype.Service
@org.springframework.transaction.annotation.Transactional
public class OnRaSettlementService {

    /** Service codes whose presence in the I2/35 error rows excludes a
     *  bill from the noErrorBillNoQ set. Mirrors the legacy
     *  {@code onGenRAsettle35.jsp} regex. Precompiled once instead of
     *  recompiled per row in the matcher loop. */
    private static final java.util.regex.Pattern Q_CODE_PATTERN =
            java.util.regex.Pattern.compile(
                    "Q011A|Q020A|Q130A|Q131A|Q132A|Q133A|Q140A|Q141A|Q142A");

    /** Selects which settle path to run. */
    public enum Mode {
        /** {@code onGenRAsettle.jsp}: settle all non-error bills,
         *  mark RA header status = "S". */
        STANDARD,
        /** {@code onGenRAsettle35.jsp}: I2/35 error path — also settle
         *  the bills whose error rows are all Q-codes; mark RA header
         *  status = "F". */
        I2_35_WITH_QCODES
    }

    private final RaHeaderDao raHeaderDao;
    private final RaDetailDao raDetailDao;
    private final BillingRaReportService billingRAReportService;

    public OnRaSettlementService(RaHeaderDao raHeaderDao, RaDetailDao raDetailDao, BillingRaReportService billingRAReportService) {
        this.raHeaderDao = raHeaderDao;
        this.raDetailDao = raDetailDao;
        this.billingRAReportService = billingRAReportService;
    }

    /**
     * Run the settle mutation for the given RA header.
     *
     * @param raNoStr the {@code rano} request param (the RA header ID).
     *                Must parse as a non-null integer; otherwise this method
     *                throws {@link io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException}
     *                so the calling action's exception mapping renders the
     *                validation page rather than the operator seeing a clean
     *                "Settle complete" with no rows actually settled.
     * @param mode {@link Mode#STANDARD} for {@code onGenRAsettle.jsp},
     *             {@link Mode#I2_35_WITH_QCODES} for
     *             {@code onGenRAsettle35.jsp}
     * @return {@code true} when a settle ran. Currently always returns
     *         {@code true} on the success path; kept for caller-side parity
     *         until callers migrate to void.
     */
    public boolean settle(String raNoStr, Mode mode) {
        Integer raNo = parseInt(raNoStr);
        if (raNo == null) {
            // Throw cleanly so the validation-error result page renders.
            // Returning false would let the action render SUCCESS (the
            // boolean is ignored) and operator would see "Settle complete"
            // when no rows were actually settled.
            throw newInvalidRanoException(raNoStr);
        }
        // The legacy code used proNo + "%" against the search methods, but
        // never set proNo from a request param — preserved as empty string
        // (effectively a wildcard match against any provider ohip suffix).
        String providerOhipPattern = "%";

        Set<String> errorBillNoQ = new HashSet<>();
        Set<String> errorBills = new HashSet<>();
        for (RaDetail rad : raDetailDao.search_raerror35(raNo, "I2", "35", providerOhipPattern)) {
            String account = String.valueOf(rad.getBillingNo());
            errorBills.add(account);
            String svcCode = rad.getServiceCode();
            if (svcCode != null && !Q_CODE_PATTERN.matcher(svcCode).matches()) {
                errorBillNoQ.add(account);
            }
        }

        // LinkedHashSet to dedupe overlapping query results (the I2/35-with-
        // Q-codes mode pulls a second set of accounts that may overlap the
        // first list). The legacy ArrayList caused repeated
        // updateBillingStatus(account, "S") writes for the same bill, which
        // wastes a tx round-trip and pollutes the audit log. The set
        // preserves insertion order so the settle order matches the legacy
        // shape for any caller that snapshots billing_on_repo.
        Set<String> noErrorBills = new java.util.LinkedHashSet<>();
        for (Integer r : raDetailDao.search_ranoerror35(raNo, "I2", "35", providerOhipPattern)) {
            String account = String.valueOf(r);
            if (!errorBills.contains(account)) {
                noErrorBills.add(account);
            }
        }

        // I2/35-with-Q-codes mode also settles bills whose I2/35 errors
        // are all Q-codes — i.e. bills not in errorBillNoQ.
        if (mode == Mode.I2_35_WITH_QCODES) {
            for (Integer r : raDetailDao.search_ranoerrorQ(raNo, providerOhipPattern)) {
                String account = String.valueOf(r);
                if (!errorBillNoQ.contains(account)) {
                    noErrorBills.add(account);
                }
            }
        }

        List<String> failedStatusUpdates = new ArrayList<>();
        for (String account : noErrorBills) {
            if (!billingRAReportService.updateBillingStatus(account, "S")) {
                failedStatusUpdates.add(account);
            }
        }
        if (!failedStatusUpdates.isEmpty()) {
            MiscUtils.getLogger().warn(
                    "RA settlement: {} billing status update(s) failed for raNo={}",
                    failedStatusUpdates.size(), raNo);
            throw new BillingValidationException(
                    "RA settlement failed for " + failedStatusUpdates.size()
                            + " bill(s): " + LogSafe.sanitize(String.join(", ", failedStatusUpdates)));
        }

        RaHeader raHeader = raHeaderDao.find(raNo);
        if (raHeader != null) {
            raHeader.setStatus(mode == Mode.STANDARD ? "S" : "F");
            raHeaderDao.merge(raHeader);
        }

        return true;
    }

    private static Integer parseInt(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            return Integer.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Helper kept out of the throw-site to keep the message constant-only
     * (the static-analysis SQL-injection regex false-positives on string
     * concatenation in exception messages). The offending input is already
     * captured upstream by the action's MiscUtils.getLogger().
     */
    private static io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException newInvalidRanoException(String raNoStr) {
        // Log the offending raw value with sanitisation so we keep diagnostic
        // context out of the user-facing exception message.
        MiscUtils.getLogger().warn("RA settle: rano parse rejected, raw value: {}", // NOSONAR javasecurity:S5145 - sanitized with LogSafe
                io.github.carlos_emr.carlos.utility.LogSafe.sanitize(raNoStr));
        return new io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException(
                "RA settle rejected: rano parameter is missing or not a valid integer");
    }
}
