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
package io.github.carlos_emr.carlos.demographic.cdsexport.dto;

import java.util.*;

/** Protected immutable artifact context; opaque owned members only, never a client file path or a saved permission grant. */
public record CdsExportArtifactContextDto(int protocol, String runId, long epoch, long patients,
        String compatibilityDigest, String recipientFingerprint, String auditPlanDigest,
        CdsExportStagedFileDto cohort, CdsExportStagedFileDto authorization, CdsExportStagedFileDto ownership,
        CdsExportStagedFileDto readMe, CdsExportStagedFileDto eventLog, CdsExportPackageDto archive,
        CdsExportStagedFileDto ciphertext, CdsExportDeliveryDto delivery) {
    public CdsExportArtifactContextDto {
        if (protocol != 1 || runId == null || !UUID.fromString(runId).toString().equals(runId) || epoch < 1 || patients < 1
                || !hash(compatibilityDigest) || !hash(recipientFingerprint) || !hash(auditPlanDigest)
                || !purpose(cohort, "COHORT_MANIFEST") || !purpose(authorization, "DISCLOSURE_MANIFEST")
                || !purpose(ownership, "ATTEMPT_MEMBERS") || !purpose(readMe, "SUPPORTING_README") || !purpose(eventLog, "SUPPORTING_EVENT_LOG")
                || archive == null || (ciphertext == null) != (delivery == null)) invalid();
        var ids = new HashSet<String>();
        for (var member : List.of(cohort, authorization, ownership, readMe, eventLog, archive.archive())) if (!ids.add(member.memberId())) invalid();
        if (ciphertext != null && (!purpose(ciphertext, "DELIVERY_CMS") || !ids.add(ciphertext.memberId())
                || !PROFILES.contains(delivery.encryptionProfile())
                || !recipientFingerprint.equals(delivery.recipientFingerprint()) || !archive.archive().plaintextSha256().equals(delivery.zipSha256())
                || archive.archive().plaintextBytes() != delivery.zipBytes() || !ciphertext.plaintextSha256().equals(delivery.ciphertextSha256())
                || ciphertext.plaintextBytes() != delivery.ciphertextBytes())) invalid();
    }
    /** One CMS object, or a tar archive of CMS parts for a package larger than one AES-GCM message. */
    private static final Set<String> PROFILES =
            Set.of("cms-aes256gcm-rsa-oaep-sha256-v1", "tar-cms-aes256gcm-rsa-oaep-sha256-v2");
    public boolean encrypted() { return ciphertext != null; }
    private static boolean hash(String value) { return value != null && value.matches("[0-9a-f]{64}"); }
    private static boolean purpose(CdsExportStagedFileDto file, String expected) { return file != null && file.purpose().equals(expected); }
    private static void invalid() { throw new IllegalArgumentException("CDS_EXPORT_ARTIFACT_CONTEXT_INVALID"); }
    @Override public String toString() { return "CdsExportArtifactContextDto[protected]"; }
}
