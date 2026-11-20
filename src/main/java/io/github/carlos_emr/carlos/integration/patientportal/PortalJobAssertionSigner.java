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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.security.PrivateKey;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Signs the {@code X-CARLOS-Job-Assertion} a CARLOS job sends with each portal call, verified by
 * the portal's {@code job_identity.py}.
 *
 * <p>It is the staff assertion's compact {@code base64url(json).base64url(ed25519-signature)}
 * format and request hash, with its own audience, the job's own key, no provider claims, and exactly
 * one permission. The payload is exactly {@code aud}, {@code clinic_id}, {@code exp}, {@code iat},
 * {@code iss}, {@code job}, {@code jti}, {@code kid}, {@code permission} and {@code request_hash},
 * written in that (sorted) order so the shared test vectors match byte for byte. The bearer service
 * token is still sent beside it.
 *
 * <p>A signer holds only its own job's key: it is built from a {@link PortalJobKey} and never sees
 * the staff key. It is immutable and creates a fresh {@code Signature} per call, so one signer may
 * sign concurrently.
 *
 * @since 2026-10-20
 */
public final class PortalJobAssertionSigner {

    public static final String HEADER = "X-CARLOS-Job-Assertion";
    /** Not the staff audience, so a job assertion can never be accepted as a staff one. */
    static final String AUDIENCE = "carlos-patient-portal-job-api";
    static final String ISSUER = PortalStaffAssertionSigner.ISSUER;
    /**
     * The same 60 seconds as staff assertions; the portal allows at most 120. {@link
     * PatientPortalSettings} keeps the request deadline inside it for both.
     */
    static final Duration LIFETIME = PortalStaffAssertionSigner.LIFETIME;

    private static final String SIGNING_FAILED = "could not sign the portal job assertion";
    private static final String PERMISSION_NOT_HELD = "portal job does not hold the permission it asked to sign";

    private final PortalJob job;
    private final String keyId;
    private final PrivateKey privateKey;
    private final Clock clock;
    private final Supplier<UUID> assertionIds;
    private final ObjectMapper objectMapper;

    /** A signer for the job the key belongs to, with the system clock and random assertion ids. */
    public static PortalJobAssertionSigner from(PortalJobKey key) {
        if (key == null) {
            throw new PatientPortalConfigurationException("portal job key is required");
        }
        return new PortalJobAssertionSigner(key.job(), key.keyId(),
                PortalStaffAssertionSigner.parsePrivateKey(key.privateKey(), key.job().privateKeyProperty()),
                Clock.systemUTC(), UUID::randomUUID, new ObjectMapper());
    }

    /** Test seam: a fixed time and assertion id give the shared vectors' exact bytes. */
    PortalJobAssertionSigner(PortalJob job, String keyId, PrivateKey privateKey, Clock clock,
            Supplier<UUID> assertionIds, ObjectMapper objectMapper) {
        if (job == null || keyId == null || privateKey == null || clock == null || assertionIds == null
                || objectMapper == null) {
            throw new PortalRequestPreparationException("portal job assertion signer dependencies are required");
        }
        this.job = job;
        this.keyId = keyId;
        this.privateKey = privateKey;
        this.clock = clock;
        this.assertionIds = assertionIds;
        this.objectMapper = objectMapper;
    }

    /** The job this signer signs as. */
    public PortalJob job() {
        return job;
    }

    /** The key id the portal looks the public key up by, under this job only. Not secret. */
    public String keyId() {
        return keyId;
    }

    /**
     * Signs one request as this job, claiming one permission.
     *
     * @param permission the single permission the call needs; it must be one the job holds
     * @throws PortalRequestPreparationException if the job does not hold {@code permission}
     */
    String sign(String permission, String clinicId, String method, URI uri, byte[] body) {
        if (permission == null || !job.permissions().contains(permission)) {
            throw new PortalRequestPreparationException(PERMISSION_NOT_HELD);
        }
        if (clinicId == null || clinicId.isBlank()) {
            throw new PortalRequestPreparationException("portal job assertion requires a clinic id");
        }
        long issuedAt = clock.instant().getEpochSecond();
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("aud", AUDIENCE);
        payload.put("clinic_id", clinicId);
        payload.put("exp", issuedAt + LIFETIME.toSeconds());
        payload.put("iat", issuedAt);
        payload.put("iss", ISSUER);
        payload.put("job", job.jobName());
        payload.put("jti", assertionIds.get().toString());
        payload.put("kid", keyId);
        payload.put("permission", permission);
        payload.put("request_hash", PortalStaffAssertionSigner.requestHash(method, uri, body));
        return PortalStaffAssertionSigner.signCompact(privateKey, objectMapper, payload, SIGNING_FAILED);
    }

    @Override
    public String toString() {
        return "PortalJobAssertionSigner[job=" + job.jobName() + ", keyId=" + keyId + ", key=REDACTED]";
    }
}
