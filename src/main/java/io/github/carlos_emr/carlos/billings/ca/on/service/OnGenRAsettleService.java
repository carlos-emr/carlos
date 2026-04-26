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
package io.github.carlos_emr.carlos.billings.ca.on.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.carlos_emr.carlos.commn.dao.RaDetailDao;
import io.github.carlos_emr.carlos.commn.dao.RaHeaderDao;
import io.github.carlos_emr.carlos.commn.model.RaDetail;
import io.github.carlos_emr.carlos.commn.model.RaHeader;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.billings.ca.on.pageUtil.BillingRAPrep;

/**
 * Mutation service for the two RA settlement popups —
 * {@code onGenRAsettle.jsp} (settle all non-error bills, mark RA header
 * as "S") and {@code onGenRAsettle35.jsp} (the I2/35 error variant with
 * special-case Q-code allow-list).
 *
 * <p>Owns the 3 inline {@code SpringUtils.getBean} lookups each JSP used
 * to perform (RaHeaderDao, BillingDao, RaDetailDao) plus the
 * {@code BillingRAPrep.updateBillingStatus} mutation. Both pages emit a
 * single client-side {@code self.close()} script after the mutation —
 * no view-model state is exposed; the action layer just calls
 * {@link #settle} and forwards to a tiny rendering JSP.</p>
 *
 * @since 2026-04-26
 */
@org.springframework.stereotype.Service
@org.springframework.context.annotation.Lazy
@org.springframework.transaction.annotation.Transactional
public class OnGenRAsettleService {

    /** Service codes whose presence in the I2/35 error rows excludes a
     *  bill from the noErrorBillNoQ set. Mirrors the legacy
     *  {@code onGenRAsettle35.jsp} regex. */
    private static final String Q_CODE_REGEX =
            "Q011A|Q020A|Q130A|Q131A|Q132A|Q133A|Q140A|Q141A|Q142A";

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

    public OnGenRAsettleService() {
        this(SpringUtils.getBean(RaHeaderDao.class),
             SpringUtils.getBean(RaDetailDao.class));
    }

    OnGenRAsettleService(RaHeaderDao raHeaderDao, RaDetailDao raDetailDao) {
        this.raHeaderDao = raHeaderDao;
        this.raDetailDao = raDetailDao;
    }

    /**
     * Run the settle mutation for the given RA header.
     *
     * @param raNoStr the {@code rano} request param (the RA header ID)
     * @param mode {@link Mode#STANDARD} for {@code onGenRAsettle.jsp},
     *             {@link Mode#I2_35_WITH_QCODES} for
     *             {@code onGenRAsettle35.jsp}
     * @return {@code true} when a settle ran (raNoStr was a valid integer
     *         and resolved to an RA header). {@code false} when the input
     *         was missing/invalid — the JSP renders nothing in that case.
     */
    public boolean settle(String raNoStr, Mode mode) {
        Integer raNo = parseInt(raNoStr);
        if (raNo == null) {
            return false;
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
            if (svcCode != null && !svcCode.matches(Q_CODE_REGEX)) {
                errorBillNoQ.add(account);
            }
        }

        List<String> noErrorBills = new ArrayList<>();
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

        BillingRAPrep prep = new BillingRAPrep();
        for (String account : noErrorBills) {
            prep.updateBillingStatus(account, "S");
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
}
