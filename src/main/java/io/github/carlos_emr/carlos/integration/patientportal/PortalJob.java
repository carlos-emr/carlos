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

import java.util.Set;

/**
 * The CARLOS jobs that call the portal with no person behind them.
 *
 * <p>A job is never a CARLOS provider. It signs its own {@code X-CARLOS-Job-Assertion} with its own
 * Ed25519 key, separate from the staff key and from every other job's, and each call claims exactly
 * one of the permissions listed here. The portal keeps the same list in code
 * ({@code system_jobs.py}), so adding a job, or giving one another permission, is a reviewed change
 * on both sides rather than a configuration line.
 *
 * <p>Rows a job writes in CARLOS name the built-in system provider
 * ({@link io.github.carlos_emr.carlos.commn.model.Provider#SYSTEM_PROVIDER_NO}); the log names the
 * job itself, as {@code PortalJob.<job>.<event>}.
 *
 * @since 2026-10-20
 */
public enum PortalJob {

    /** Lists patients' picks of offered times and reports whether CARLOS booked them. */
    BOOKING_SYNC("booking-sync", Set.of(PortalJob.PERMISSION_BOOKING_PROMPT_SYNC));

    /**
     * List patients' picks of offered times and report whether they were booked. Only a job holds
     * it: the portal refuses a staff assertion that carries it, on every route.
     */
    public static final String PERMISSION_BOOKING_PROMPT_SYNC = "portal.booking_prompt.sync";

    private static final String PROPERTY_PREFIX = "patient_portal.job.";

    private final String jobName;
    private final Set<String> permissions;

    PortalJob(String jobName, Set<String> permissions) {
        this.jobName = jobName;
        this.permissions = Set.copyOf(permissions);
    }

    /** The registered name the portal knows the job by, and the {@code job} claim. */
    public String jobName() {
        return jobName;
    }

    /** Every permission the job may claim, one per call. */
    public Set<String> permissions() {
        return permissions;
    }

    /** The job's private key: unpadded base64url PKCS#8, protected like the staff key. */
    public String privateKeyProperty() {
        return PROPERTY_PREFIX + jobName + ".private_key";
    }

    /** The job's key id: {@code [A-Za-z0-9._-]{1,64}}, never the staff key id or another job's. */
    public String keyIdProperty() {
        return PROPERTY_PREFIX + jobName + ".key_id";
    }

    /** The audit log action for one thing the job did, for example {@code PortalJob.booking-sync.booked}. */
    public String logAction(String event) {
        return "PortalJob." + jobName + "." + event;
    }
}
