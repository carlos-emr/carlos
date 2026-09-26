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
 *
 * Provider linking rules were first implemented by Deval Italiya in
 * open-osp/Open-O pull request #196 (GPL); this CARLOS implementation is
 * adapted from that work.
 */
package io.github.carlos_emr.carlos.hospitalReportManager.service;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.carlos_emr.carlos.commn.dao.IncomingLabRulesDao;
import io.github.carlos_emr.carlos.commn.model.IncomingLabRules;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentToProviderDao;
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMDocumentToProvider;

/**
 * Routes an HRM report to a provider's inbox, the way a manual "assign provider" does.
 *
 * <p>One routing, three steps, always together:</p>
 * <ol>
 *   <li>add an unsigned {@code HRMDocumentToProvider} row for the provider, unless one exists;</li>
 *   <li>apply that provider's current HRM forwarding rules ({@code IncomingLabRules} whose forward
 *       types include {@code HRM}), again only for providers not already routed;</li>
 *   <li>drop the report's unclaimed ({@code -1}) rows, now that a real provider holds it. This is
 *       a bulk delete: see {@code HRMDocumentToProviderDao#deleteByHrmDocumentIdAndProviderNo}.</li>
 * </ol>
 *
 * <p>{@code HRMDocumentToProvider} has no unique key on (report, provider), so every step checks
 * before it inserts: a duplicate row survives the first one's sign-off and keeps the report in
 * the inbox. Callers are expected to hold the report lock
 * ({@code HRMDocumentDao.findForUpdate}); the methods join the caller's transaction.</p>
 *
 * @since 2026-09-26
 */
@Service
public class HrmProviderRoutingService {

    /** Provider number HRM uses for a report no real provider has claimed. */
    public static final String UNCLAIMED_PROVIDER_NO = "-1";

    private static final String HRM_FORWARD_TYPE = "HRM";

    private final HRMDocumentToProviderDao hrmDocumentToProviderDao;
    private final IncomingLabRulesDao incomingLabRulesDao;

    public HrmProviderRoutingService(HRMDocumentToProviderDao hrmDocumentToProviderDao,
                                     IncomingLabRulesDao incomingLabRulesDao) {
        this.hrmDocumentToProviderDao = hrmDocumentToProviderDao;
        this.incomingLabRulesDao = incomingLabRulesDao;
    }

    /**
     * Routes the report to the provider, applies their HRM forwarding rules and removes the
     * unclaimed rows.
     *
     * @param hrmDocumentId the report, which must exist
     * @param providerNo the provider to route to
     * @return {@code true} when a routing row was created for {@code providerNo} itself;
     *         {@code false} when the provider already had one
     */
    @Transactional
    public boolean assignProvider(int hrmDocumentId, String providerNo) {
        boolean created = addRoutingIfAbsent(hrmDocumentId, providerNo);
        applyForwardingRules(hrmDocumentId, providerNo);
        removeUnclaimedRouting(hrmDocumentId);
        return created;
    }

    private boolean addRoutingIfAbsent(int hrmDocumentId, String providerNo) {
        List<HRMDocumentToProvider> existing =
                hrmDocumentToProviderDao.findByHrmDocumentIdAndProviderNoList(hrmDocumentId, providerNo);
        if (existing != null && !existing.isEmpty()) {
            return false;
        }
        HRMDocumentToProvider routing = new HRMDocumentToProvider();
        routing.setHrmDocumentId(hrmDocumentId);
        routing.setProviderNo(providerNo);
        routing.setSignedOff(0);
        hrmDocumentToProviderDao.persist(routing);
        return true;
    }

    private void applyForwardingRules(int hrmDocumentId, String providerNo) {
        List<IncomingLabRules> rules = incomingLabRulesDao.findCurrentByProviderNo(providerNo);
        if (rules == null) {
            return;
        }
        for (IncomingLabRules rule : rules) {
            String forwardTo = rule.getFrwdProviderNo();
            // Forwarding is one hop, as it always was for HRM: the forwarded provider's own rules
            // are not followed, so two providers forwarding to each other cannot loop.
            if (StringUtils.isNotBlank(forwardTo) && rule.getForwardTypeStrings().contains(HRM_FORWARD_TYPE)) {
                addRoutingIfAbsent(hrmDocumentId, forwardTo);
            }
        }
    }

    private void removeUnclaimedRouting(int hrmDocumentId) {
        // Every unclaimed row, not the last one only: a survivor keeps the report on the "all"
        // view. A bulk delete, because the caller's findForUpdate lock has loaded these rows into
        // HRMDocument.matchedProviders; EntityManager.remove() on one of them made the next flush
        // throw TransientPropertyValueException and roll the whole assignment back.
        hrmDocumentToProviderDao.deleteByHrmDocumentIdAndProviderNo(hrmDocumentId, UNCLAIMED_PROVIDER_NO);
    }
}
