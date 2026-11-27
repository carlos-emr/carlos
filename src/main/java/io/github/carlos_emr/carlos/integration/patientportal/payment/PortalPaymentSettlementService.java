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
package io.github.carlos_emr.carlos.integration.patientportal.payment;

import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalSettings;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalStaffContext;
import io.github.carlos_emr.carlos.integration.patientportal.PortalStaffContextResolver;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import jakarta.annotation.PreDestroy;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Closes an invoice's portal payment requests after a payment was saved in CARLOS's third-party
 * payment screen. It runs <em>after</em> the payment transaction has committed and never throws:
 * the payment is already recorded, and a portal problem must not turn that into an error page.
 *
 * <p>The local closing is synchronous and needs no portal privilege. Telling the portal needs one
 * and runs on a background thread, so a slow portal never delays the answer: a billing clerk who
 * lacks the portal payment object still closes the request here and leaves the push for the next
 * person who views the invoice's requests.
 */
@Service
public class PortalPaymentSettlementService {
    private static final Logger LOGGER = MiscUtils.getLogger();

    /** Pending reports beyond this many are dropped, never queued without bound. */
    static final int REPORT_QUEUE_CAPACITY = 50;

    private final PortalPaymentRequestService requests;
    private final Supplier<PortalStaffContextResolver> resolver;
    private final BooleanSupplier portalConfigured;
    private final Executor reporter;
    /** The pool this bean created (null when a test supplied its own executor); shut down with the context. */
    private final ExecutorService owned;

    /**
     * This singleton owns the one pool that tells the portal: a single daemon thread and a bounded
     * queue, created with the bean and shut down with the application context, so no thread is
     * created per request. A slow or hung portal (up to its request timeout per call) never delays
     * the clerk's "payment saved" answer, and a full queue drops the report instead of growing.
     * Nothing depends on the task running: a dropped or lost report (queue full, restart) leaves
     * the row paid and unsynced, and the next load of the invoice's payment requests or the next
     * payment save for that invoice reports it ({@link PortalPaymentRequestService#reportPaid}).
     */
    @Autowired
    public PortalPaymentSettlementService(PortalPaymentRequestService requests) {
        this(requests, () -> SpringUtils.getBean(PortalStaffContextResolver.class),
                PatientPortalSettings::isConfigured, boundedReporter());
    }

    private PortalPaymentSettlementService(PortalPaymentRequestService requests,
            Supplier<PortalStaffContextResolver> resolver, BooleanSupplier portalConfigured,
            ExecutorService pool) {
        this(requests, resolver, portalConfigured, (Executor) pool, pool);
    }

    PortalPaymentSettlementService(PortalPaymentRequestService requests,
            Supplier<PortalStaffContextResolver> resolver, BooleanSupplier portalConfigured, Executor reporter) {
        this(requests, resolver, portalConfigured, reporter, null);
    }

    private PortalPaymentSettlementService(PortalPaymentRequestService requests,
            Supplier<PortalStaffContextResolver> resolver, BooleanSupplier portalConfigured, Executor reporter,
            ExecutorService owned) {
        this.requests = requests;
        this.resolver = resolver;
        this.portalConfigured = portalConfigured;
        this.reporter = reporter;
        this.owned = owned;
    }

    private static ExecutorService boundedReporter() {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(REPORT_QUEUE_CAPACITY), task -> {
                    Thread thread = new Thread(task, "portal-payment-report");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
        return pool;
    }

    /** Stops the pool with the application context; reports still queued are retried by the next view. */
    @PreDestroy
    public void shutdown() {
        if (owned != null) {
            owned.shutdownNow();
        }
    }

    /**
     * @return how many requests were closed locally; 0 when the portal is not configured or nothing
     *     was open for the invoice
     */
    public int afterPaymentSaved(LoggedInInfo session, int billingNo) {
        if (!portalConfigured.getAsBoolean()) {
            return 0;
        }
        try {
            int closed = requests.settleLocally(billingNo);
            PatientPortalStaffContext staff = null;
            try {
                staff = resolver.get().resolve(session, Set.of(PortalStaffContextResolver.OBJECT_PAYMENT_REQUEST));
            } catch (SecurityException withoutPortalPrivilege) {
                // Closed locally above; the portal is told by someone who may.
            }
            if (staff != null) {
                PatientPortalStaffContext asked = staff;
                try {
                    reporter.execute(() -> {
                        try {
                            requests.reportPaid(asked, billingNo);
                        } catch (RuntimeException failed) {
                            LOGGER.warn("Portal payment requests were not reported after a payment; exceptionClass={}",
                                    failed.getClass().getSimpleName());
                        }
                    });
                } catch (RejectedExecutionException dropped) {
                    // Queue full or pool shut down: the report is dropped, the row stays paid and
                    // unsynced, and the next view or payment save for the invoice reports it.
                    LOGGER.warn("A portal payment report was dropped; exceptionClass={}",
                            dropped.getClass().getSimpleName());
                }
            }
            return closed;
        } catch (RuntimeException failed) {
            LOGGER.warn("Portal payment requests were not settled after a payment; exceptionClass={}",
                    failed.getClass().getSimpleName());
            return 0;
        }
    }
}
