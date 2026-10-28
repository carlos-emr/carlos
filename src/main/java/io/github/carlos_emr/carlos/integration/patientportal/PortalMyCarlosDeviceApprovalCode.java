/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
package io.github.carlos_emr.carlos.integration.patientportal;

import static io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalService.Checks.*;
import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalTypes.Bytes;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * The code a clinic emailed for one "code" challenge (clinic code v1, mycarlos#141): its keyed hash,
 * never the code. Open until it matches or the fifth wrong code refuses the request.
 */
record PortalMyCarlosDeviceApprovalCode(Bytes challengeId, String clinicId, UUID requestId, String policy,
        Bytes keyId, Bytes mac, int wrongAttempts, Instant issuedAt, Instant expiresAt, Instant emailedAt,
        Instant matchedAt, Instant refusedAt, long version) {
    /** Wrong codes after which the request is refused for good ({@code code_attempts}). */
    static final int MAX_WRONG = 5;

    PortalMyCarlosDeviceApprovalCode {
        bytes(challengeId, 32);
        text(clinicId, 256);
        uuid(requestId);
        require("code".equals(policy) || "code_and_staff".equals(policy));
        bytes(keyId, 16);
        bytes(mac, 32);
        require(wrongAttempts >= 0 && wrongAttempts <= MAX_WRONG);
        second(issuedAt);
        second(expiresAt);
        require(expiresAt.isAfter(issuedAt) && Duration.between(issuedAt, expiresAt).getSeconds() <= 600);
        if (emailedAt != null) time(emailedAt);
        if (matchedAt != null) time(matchedAt);
        if (refusedAt != null) time(refusedAt);
        require(matchedAt == null || refusedAt == null);
        require(refusedAt == null || wrongAttempts == MAX_WRONG);
        nonnegative(version);
    }

    /** @return whether a submitted code can still decide this request */
    boolean open() {
        return matchedAt == null && refusedAt == null;
    }

    /**
     * A new code in place of one that was never emailed: that code's value lived only in a run that
     * ended (the send failed or the server stopped), so it can never be typed.
     */
    PortalMyCarlosDeviceApprovalCode reissued(Bytes newKeyId, Bytes newMac) {
        require(open() && emailedAt == null && wrongAttempts == 0);
        return new PortalMyCarlosDeviceApprovalCode(challengeId, clinicId, requestId, policy, newKeyId, newMac, 0,
                issuedAt, expiresAt, null, null, null, Math.addExact(version, 1));
    }

    PortalMyCarlosDeviceApprovalCode emailed(Instant now) {
        require(emailedAt == null);
        return next(wrongAttempts, now, matchedAt, refusedAt);
    }

    PortalMyCarlosDeviceApprovalCode matched(Instant now) {
        require(open());
        return next(wrongAttempts, emailedAt, now, null);
    }

    /** One more wrong code; the fifth also refuses the request. */
    PortalMyCarlosDeviceApprovalCode wrong(Instant now) {
        require(open());
        int count = wrongAttempts + 1;
        return next(count, emailedAt, null, count == MAX_WRONG ? now : null);
    }

    private PortalMyCarlosDeviceApprovalCode next(int wrong, Instant emailed, Instant matched, Instant refused) {
        return new PortalMyCarlosDeviceApprovalCode(challengeId, clinicId, requestId, policy, keyId, mac, wrong,
                issuedAt, expiresAt, emailed, matched, refused, Math.addExact(version, 1));
    }

    @Override
    public String toString() {
        return "DeviceApprovalCode[REDACTED]";
    }
}
