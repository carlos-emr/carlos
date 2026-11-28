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

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.model.PortalPaymentRequest;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsImportMaintenanceService;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalConfigurationException;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalException;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalService;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalSettings;
import io.github.carlos_emr.carlos.integration.patientportal.PortalJob;
import io.github.carlos_emr.carlos.integration.patientportal.PortalJobAssertionSigner;
import io.github.carlos_emr.carlos.integration.patientportal.PortalJobKey;
import io.github.carlos_emr.carlos.utility.DeamonThreadFactory;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** The hourly job reports payments already settled locally. It never writes a billing payment. */
@Service
public class PortalPaymentRetryJob {
    private static final Logger LOGGER = MiscUtils.getLogger();
    static final int PAGE_SIZE = 100;
    static final String ENABLED = "patient_portal.payment.settlement.enabled";
    private final PortalPaymentRequestService requests;
    private final BooleanSupplier enabled;
    private final BooleanSupplier maintenance;
    private ScheduledExecutorService executor;
    private long afterId;

    @Autowired
    public PortalPaymentRetryJob(PortalPaymentRequestService requests, CdsImportMaintenanceService maintenance) {
        this(requests, () -> Boolean.parseBoolean((String) CarlosProperties.getInstance().get(ENABLED))
                && PatientPortalSettings.isConfigured(), maintenance::isClinicalAccessBlocked);
    }

    PortalPaymentRetryJob(PortalPaymentRequestService requests, BooleanSupplier enabled, BooleanSupplier maintenance) {
        this.requests = requests;
        this.enabled = enabled;
        this.maintenance = maintenance;
    }

    @PostConstruct
    public void start() {
        if (!enabled.getAsBoolean()) {
            return;
        }
        PortalJobAssertionSigner job;
        try {
            job = PortalJobKey.fromCarlosProperties(PortalJob.PAYMENT_SETTLEMENT)
                    .map(PortalJobAssertionSigner::from).orElse(null);
        } catch (PatientPortalConfigurationException invalid) {
            LOGGER.warn("Portal payment settlement is off because the job key is invalid");
            return;
        }
        if (job == null) {
            LOGGER.warn("Portal payment settlement is off because the job key is missing");
            return;
        }
        executor = Executors.newSingleThreadScheduledExecutor(
                new DeamonThreadFactory(getClass().getSimpleName(), Thread.NORM_PRIORITY));
        executor.scheduleWithFixedDelay(() -> runSafely(job), 1, 1, TimeUnit.HOURS);
    }

    @PreDestroy
    public void shutdown() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    void runSafely(PortalJobAssertionSigner job) {
        try {
            if (paused()) {
                return;
            }
            List<PortalPaymentRequest> page = requests.paidPageAfter(afterId, PAGE_SIZE);
            for (PortalPaymentRequest row : page) {
                if (paused()) {
                    return;
                }
                try {
                    requests.reportSettlement(row, job);
                } catch (PatientPortalException failure) {
                    LOGGER.warn("Portal payment settlement deferred; kind={}", failure.kind());
                    // A row may still be awaiting its remote create. Continue past that row,
                    // but avoid sending a batch to an unavailable portal or using a refused key.
                    if (failure.kind() == PatientPortalException.Kind.TRANSPORT_FAILURE
                            || failure.kind() == PatientPortalException.Kind.THROTTLED
                            || failure.statusCode() >= 500 || failure.statusCode() == 401
                            || failure.statusCode() == 403 || (failure.statusCode() == 404
                            && !PatientPortalService.PAYMENT_REQUEST_NOT_FOUND_DETAIL.equals(failure.detail()))) {
                        return;
                    }
                }
                afterId = row.getId();
            }
            if (page.size() < PAGE_SIZE) {
                afterId = 0;
            }
        } catch (RuntimeException failure) {
            // A thrown task cancels future fixed-delay runs. Durable rows must survive any outage.
            LOGGER.warn("Portal payment settlement run deferred; exceptionClass={}",
                    failure.getClass().getSimpleName());
        }
    }

    private boolean paused() {
        return Thread.currentThread().isInterrupted() || !enabled.getAsBoolean() || maintenance.getAsBoolean();
    }
}
