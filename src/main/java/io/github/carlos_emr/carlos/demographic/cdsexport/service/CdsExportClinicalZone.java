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
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.zone.ZoneRulesProvider;
import java.util.*;

/** Frozen explicit clinic zone and installed rules version; recovery never adopts today's setting or changed rules. */
final class CdsExportClinicalZone {
    record Snapshot(String zoneId, String tzdbVersion, String sha256) {
        @Override
        public String toString() {
            return "CdsExportClinicalZone.Snapshot[protected]";
        }
    }

    private CdsExportClinicalZone() {}

    static Snapshot capture(String configured) throws IOException {
        if (configured == null || configured.isEmpty())
            throw new IOException("CDS_EXPORT_CLINICAL_ZONE_REQUIRED");
        if (configured.length() > 255 || configured.codePoints().anyMatch(Character::isWhitespace))
            invalid();
        try {
            var zone = ZoneId.of(configured);
            String version =
                    zone.getRules().isFixedOffset()
                            ? "fixed." + zone.getRules().getOffset(Instant.EPOCH).getTotalSeconds()
                            : ZoneRulesProvider.getVersions(zone.getId()).lastKey();
            if (version == null || !version.matches("[A-Za-z0-9._+-]{1,128}")) invalid();
            String id = zone.getId();
            return new Snapshot(id, version, digest(id, version));
        } catch (DateTimeException | NoSuchElementException failed) {
            invalid();
            return null;
        }
    }

    static ZoneId restore(String id, String version, String sha256) throws IOException {
        if (id == null
                || version == null
                || sha256 == null
                || !sha256.matches("[0-9a-f]{64}")
                || !sha256.equals(digest(id, version))) invalid();
        var current = capture(id);
        if (!current.zoneId().equals(id) || !current.tzdbVersion().equals(version))
            throw new IOException("CDS_EXPORT_CLINICAL_ZONE_VERSION_REQUIRES_REVIEW");
        return ZoneId.of(id);
    }

    private static String digest(String zone, String version) {
        return HexFormat.of()
                .formatHex(
                        CdsImportKeys.digest()
                                .digest(
                                        ("CDS_CLINICAL_ZONE_V1\0" + zone + "\0" + version)
                                                .getBytes(StandardCharsets.US_ASCII)));
    }

    private static void invalid() throws IOException {
        throw new IOException("CDS_EXPORT_CLINICAL_ZONE_INVALID");
    }
}
