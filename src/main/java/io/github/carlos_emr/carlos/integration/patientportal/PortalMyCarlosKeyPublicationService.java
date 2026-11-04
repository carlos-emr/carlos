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
package io.github.carlos_emr.carlos.integration.patientportal;

import io.github.carlos_emr.carlos.utility.DeamonThreadFactory;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import java.util.function.Supplier;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Separately enabled publication of an explicitly provisioned whole public-key catalogue.
 *
 * <p>HTTP runs after a durable lease transaction ends. Only an exact same-target/generation
 * acknowledgement can mark it published. Neither this job nor its retries load a document signer.
 */
@Service
public class PortalMyCarlosKeyPublicationService {
    static final Duration LEASE = Duration.ofMinutes(2);
    static final int INTERVAL_SECONDS = 30;
    private static final Logger LOGGER = MiscUtils.getLogger();
    private static final SecureRandom RANDOM = new SecureRandom();
    private final PortalMyCarlosKeyInventoryService repository;
    private final BooleanSupplier enabled;
    private final BooleanSupplier portalConfigured;
    private final Supplier<PatientPortalService> portal;
    private final Function<PortalJob, Optional<PortalJobKey>> keys;
    private final Clock clock;
    private final Supplier<String> tokens;
    private final DoubleSupplier jitter;
    private ScheduledExecutorService executor;

    @Autowired
    public PortalMyCarlosKeyPublicationService(PortalMyCarlosKeyInventoryService repository) {
        this(repository, PortalMyCarlosDeliverySettings::isEnabled, PatientPortalSettings::isConfigured,
                () -> SpringUtils.getBean(PatientPortalService.class), PortalJobKey::fromCarlosProperties,
                Clock.systemUTC(), PortalMyCarlosKeyPublicationService::newToken,
                RANDOM::nextDouble);
    }

    PortalMyCarlosKeyPublicationService(
            PortalMyCarlosKeyInventoryService repository, BooleanSupplier enabled,
            BooleanSupplier portalConfigured, Supplier<PatientPortalService> portal,
            Function<PortalJob, Optional<PortalJobKey>> keys, Clock clock,
            Supplier<String> tokens, DoubleSupplier jitter) {
        this.repository = Objects.requireNonNull(repository);
        this.enabled = Objects.requireNonNull(enabled);
        this.portalConfigured = Objects.requireNonNull(portalConfigured);
        this.portal = Objects.requireNonNull(portal);
        this.keys = Objects.requireNonNull(keys);
        this.clock = Objects.requireNonNull(clock);
        this.tokens = Objects.requireNonNull(tokens);
        this.jitter = Objects.requireNonNull(jitter);
    }

    @PostConstruct
    public synchronized void start() {
        if (executor != null) {
            return;
        }
        try {
            // Short-circuit before reading any job key, client, catalogue or document signer.
            if (!enabled.getAsBoolean() || !portalConfigured.getAsBoolean()) {
                return;
            }
            PortalJobAssertionSigner job = keys.apply(PortalJob.KEY_PUBLISH)
                    .map(PortalJobAssertionSigner::from).orElse(null);
            if (job == null || job.job() != PortalJob.KEY_PUBLISH) {
                LOGGER.warn("myCarlos key publication is off: the key-publish job key is not configured");
                return;
            }
            executor = Executors.newSingleThreadScheduledExecutor(
                    new DeamonThreadFactory(getClass().getSimpleName(), Thread.NORM_PRIORITY));
            executor.scheduleWithFixedDelay(() -> runSafely(job), INTERVAL_SECONDS,
                    INTERVAL_SECONDS, TimeUnit.SECONDS);
        } catch (RuntimeException failure) {
            LOGGER.warn("myCarlos key publication is off; check its flag and job configuration; exceptionClass={}",
                    failure.getClass().getSimpleName());
        }
    }

    /** A bounded single attempt; callers must never wrap it in a database transaction. */
    public void runOnce(PortalJobAssertionSigner job) {
        if (!enabled.getAsBoolean() || !portalConfigured.getAsBoolean()) {
            return;
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("clinic key publication must run outside a database transaction");
        }
        if (job == null || job.job() != PortalJob.KEY_PUBLISH) {
            throw new IllegalArgumentException("the key-publish job identity is required");
        }
        Optional<PortalMyCarlosPublicationState.Claim> pending =
                repository.claimCurrent(this::currentTarget, tokens.get(), clock::instant, LEASE);
        if (pending.isEmpty()) {
            return;
        }
        var claim = pending.get();
        try {
            if (!enabled.getAsBoolean() || !portalConfigured.getAsBoolean()) return;
            PatientPortalService client = portal.get();
            if (!client.clinicKeysPublicationTarget().equals(claim.target())) {
                recordFailure(claim);
                return;
            }
            PatientPortalClinicKeysPublishResult response =
                    client.publishClinicKeys(claim.inventory().publication(), job);
            if (enabled.getAsBoolean() && portalConfigured.getAsBoolean()) {
                // Re-read the effective client after HTTP: an old A response cannot acknowledge B.
                repository.acknowledgeCurrent(
                        claim, this::currentTarget, response, clock::instant);
            }
        } catch (RuntimeException failure) {
            recordFailure(claim);
            LOGGER.warn("myCarlos key publication remains pending; exceptionClass={}",
                    failure.getClass().getSimpleName());
        }
    }

    private PortalMyCarlosPublicationState.Target currentTarget() {
        return enabled.getAsBoolean() && portalConfigured.getAsBoolean()
                ? portal.get().clinicKeysPublicationTarget() : null;
    }

    private void recordFailure(PortalMyCarlosPublicationState.Claim claim) {
        try {
            if (enabled.getAsBoolean() && portalConfigured.getAsBoolean()) {
                repository.failedCurrent(claim, this::currentTarget, clock::instant,
                        retryDelay(claim.failures(), jitter.getAsDouble()));
            }
        } catch (RuntimeException failure) {
            // A failed bookkeeping transaction leaves the durable lease pending until it expires.
            LOGGER.warn("myCarlos key publication retry bookkeeping failed; exceptionClass={}",
                    failure.getClass().getSimpleName());
        }
    }

    static Duration retryDelay(int previousFailures, double sample) {
        if (previousFailures < 0 || previousFailures > 30 || !Double.isFinite(sample)
                || sample < 0 || sample > 1) {
            throw new IllegalArgumentException("clinic key publication retry input is invalid");
        }
        long baseMillis = Math.min(900_000L, 30_000L << Math.min(previousFailures, 5));
        long jitterMillis = (long) (baseMillis * 0.2 * sample);
        return Duration.ofMillis(Math.min(900_000L, baseMillis + jitterMillis));
    }

    private void runSafely(PortalJobAssertionSigner job) {
        try {
            runOnce(job);
        } catch (RuntimeException failure) {
            LOGGER.warn("myCarlos key publication attempt failed; exceptionClass={}",
                    failure.getClass().getSimpleName());
        }
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @PreDestroy
    public synchronized void close() {
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }
}
