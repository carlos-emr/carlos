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

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;

/**
 * Local publication bookkeeping, bound to the actual portal target as well as catalogue content.
 * No field here is added to the portal request or used as a document/rotation signature.
 */
record PortalMyCarlosPublicationState(
        Target target, long generation, String contentDigest, boolean published, Instant acknowledgedAt,
        String leaseToken, Instant leaseExpiresAt, int failures, Instant nextAttemptAt) {
    static final Duration MAX_LEASE = Duration.ofMinutes(5);
    static final Duration MAX_RETRY = Duration.ofMinutes(15);
    static final String ENDPOINT_PATH = "/internal/carlos/mycarlos/clinic-keys";

    PortalMyCarlosPublicationState {
        Objects.requireNonNull(target);
        if (generation <= 0 || !validDigest(contentDigest) || failures < 0 || failures > 30
                || (leaseToken == null) != (leaseExpiresAt == null)
                || (leaseToken != null && !leaseToken.matches("[A-Za-z0-9_-]{16,128}"))
                || (published != (acknowledgedAt != null))
                || (published && (leaseToken != null || failures != 0 || nextAttemptAt != null))
                || (leaseToken != null && nextAttemptAt != null)) {
            throw invalid();
        }
    }

    /** This is the resolved internal transport endpoint, never the patient-facing public URL. */
    record Target(String clinicId, URI endpoint) {
        Target {
            if (clinicId == null || clinicId.isBlank() || clinicId.length() > 128
                    || endpoint == null || !"https".equals(endpoint.getScheme())
                    || endpoint.getHost() == null || endpoint.getUserInfo() != null
                    || endpoint.getRawQuery() != null || endpoint.getRawFragment() != null
                    || !ENDPOINT_PATH.equals(endpoint.getRawPath())
                    || (endpoint.getPort() != -1 && (endpoint.getPort() < 1 || endpoint.getPort() > 65535))) {
                throw invalid();
            }
        }

        @Override
        public String toString() {
            return "ClinicKeyPublicationTarget[details=REDACTED]";
        }
    }

    /** Immutable network snapshot, created only after a durable lease has been committed. */
    record Claim(
            PortalMyCarlosKeyInventory inventory, Target target, String token, Instant expiresAt, int failures) {
        Claim {
            Objects.requireNonNull(inventory);
            Objects.requireNonNull(target);
            if (!inventory.clinicId().equals(target.clinicId()) || token == null
                    || !token.matches("[A-Za-z0-9_-]{16,128}") || expiresAt == null
                    || failures < 0 || failures > 30) {
                throw invalid();
            }
        }

        @Override
        public String toString() {
            return "ClinicKeyPublicationClaim[details=REDACTED]";
        }
    }

    static PortalMyCarlosPublicationState pending(PortalMyCarlosKeyInventory inventory, Target target) {
        if (!inventory.clinicId().equals(target.clinicId())) {
            throw invalid();
        }
        return new PortalMyCarlosPublicationState(
                target, inventory.generation(), inventory.contentDigest(), false, null, null, null, 0, null);
    }

    boolean matches(PortalMyCarlosKeyInventory inventory, Target currentTarget) {
        return target.equals(currentTarget) && inventory.clinicId().equals(currentTarget.clinicId())
                && generation == inventory.generation() && contentDigest.equals(inventory.contentDigest());
    }

    boolean claimableAt(Instant now) {
        Objects.requireNonNull(now);
        return !published && (leaseExpiresAt == null || !now.isBefore(leaseExpiresAt))
                && (nextAttemptAt == null || !now.isBefore(nextAttemptAt));
    }

    PortalMyCarlosPublicationState leased(String token, Instant now, Duration duration) {
        if (!claimableAt(now) || duration == null || duration.isNegative() || duration.isZero()
                || duration.compareTo(MAX_LEASE) > 0) {
            throw invalid();
        }
        return new PortalMyCarlosPublicationState(target, generation, contentDigest, false, null,
                token, now.plus(duration), failures, null);
    }

    boolean owns(Claim claim, Target effectiveTargetNow, Instant now) {
        return claim != null && matches(claim.inventory(), effectiveTargetNow)
                && target.equals(claim.target()) && Objects.equals(leaseToken, claim.token())
                && Objects.equals(leaseExpiresAt, claim.expiresAt())
                && failures == claim.failures()
                && leaseExpiresAt != null && now.isBefore(leaseExpiresAt);
    }

    PortalMyCarlosPublicationState acknowledged(Instant now) {
        if (published || leaseToken == null || now == null || !now.isBefore(leaseExpiresAt)) {
            throw invalid();
        }
        return new PortalMyCarlosPublicationState(
                target, generation, contentDigest, true, now, null, null, 0, null);
    }

    PortalMyCarlosPublicationState retryAfter(Instant now, Duration delay) {
        if (published || leaseToken == null || now == null || delay == null || delay.isNegative() || delay.isZero()
                || delay.compareTo(MAX_RETRY) > 0) {
            throw invalid();
        }
        return new PortalMyCarlosPublicationState(target, generation, contentDigest, false, null,
                null, null, Math.min(30, failures + 1), now.plus(delay));
    }

    private static boolean validDigest(String digest) {
        if (digest == null || digest.length() != 43) {
            return false;
        }
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(digest);
            return bytes.length == 32
                    && Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).equals(digest);
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("clinic key publication state is invalid");
    }

    @Override
    public String toString() {
        return "ClinicKeyPublicationState[details=REDACTED]";
    }
}
