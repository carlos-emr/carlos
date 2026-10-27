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
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportActorDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportJobDto;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Bounded protected read model; current authorization precedes every clinical aggregate. */
@Service
public class CdsImportObservationService {
    private final CdsImportLedgerService ledger;
    private final CdsImportReviewService reviews;
    private final CdsImportAccessService access;
    private final TransactionTemplate transaction;

    @Autowired
    public CdsImportObservationService(CdsImportLedgerService ledger, CdsImportReviewService reviews,
            CdsImportAccessService access, PlatformTransactionManager manager) {
        this.ledger = Objects.requireNonNull(ledger);
        this.reviews = Objects.requireNonNull(reviews);
        this.access = Objects.requireNonNull(access);
        transaction = CdsImportLedgerService.template(Objects.requireNonNull(manager));
        transaction.setReadOnly(true);
    }

    /** Real dependencies also support the legacy facade constructor outside Spring. */
    CdsImportObservationService(CdsImportLedgerService ledger, CdsImportReviewService reviews,
            CdsImportAccessService access) {
        this(ledger, reviews, access, Objects.requireNonNull(ledger.observationTransactionManager()));
    }

    Map<String, Object> summary(LoggedInInfo viewer, String run) {
        CdsImportLedgerService.requireNoTransaction();
        return transaction.execute(status -> {
            var current = ledger.jobBindingInReadTransaction(run);
            access.requireView(viewer, current.job());
            return projection(run);
        });
    }

    Map<String, Object> summary(CdsImportActorDto actor, CdsImportJobService.ReadContext context) {
        CdsImportLedgerService.requireNoTransaction();
        Objects.requireNonNull(context);
        return transaction.execute(status -> {
            String run = context.job().id();
            var current = ledger.jobBindingInReadTransaction(run);
            requireActorIdentity(actor, current.job());
            context.requireCurrent(current);
            if (!context.document().actor().equals(actor)) throw new SecurityException("IMPORT_ACCESS_DENIED");
            // The facade handles a revocation only after this read-only scope has ended.
            try {
                access.context(actor, context.document().command(), null);
            } catch (SecurityException revoked) {
                throw new AuthorizationRevoked(revoked);
            }
            return projection(run);
        });
    }

    static void requireActorIdentity(CdsImportActorDto actor, CdsImportJobDto job) {
        if (actor == null || !Objects.equals(job.operator(), actor.operator())
                || !Objects.equals(job.providerNo(), actor.providerNo())) {
            throw new SecurityException("IMPORT_ACCESS_DENIED");
        }
    }

    private Map<String, Object> projection(String run) {
        Map<String, Object> result = new LinkedHashMap<>(ledger.summaryInReadTransaction(run));
        result.putAll(reviews.summaryInReadTransaction(run));
        return result;
    }

    /** Distinguishes a live-rights revocation from an unauthorized caller before any aggregate read. */
    static final class AuthorizationRevoked extends RuntimeException {
        private final SecurityException failure;

        AuthorizationRevoked(SecurityException failure) {
            super("IMPORT_AUTHORIZATION_REVOKED", failure);
            this.failure = failure;
        }

        SecurityException failure() { return failure; }
    }
}
