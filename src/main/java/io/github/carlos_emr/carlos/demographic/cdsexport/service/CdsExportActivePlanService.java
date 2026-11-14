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

import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportXmlLimitsDto;
import io.github.carlos_emr.carlos.utility.BuildInfo;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.io.IOException;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/** Lazy active-work budgets and semantic zone binding; metadata/report recovery does not parse active settings. */
@Service
public class CdsExportActivePlanService {
    public record Plan(
            String applicationVersion,
            ZoneId clinicalZone,
            String zoneSha256,
            CdsExportClinicalOccurrenceService.Bounds source,
            CdsExportXmlLimitsDto xml,
            long retentionSeconds) {}

    private final Properties settings = new Properties();
    private final Supplier<String> version;

    @org.springframework.beans.factory.annotation.Autowired
    public CdsExportActivePlanService(@Qualifier("cdsExportProperties") Properties settings) {
        this(settings, () -> BuildInfo.getInstance().getVersion());
    }

    CdsExportActivePlanService(Properties settings, Supplier<String> version) {
        this.settings.putAll(Objects.requireNonNull(settings));
        this.version = Objects.requireNonNull(version);
    }

    /**
     * Registry identity must remain available to metadata/recovery commands even when
     * admission settings are invalid. An invalid zone gets a distinct, non-executable
     * identity; resolve() still refuses it before any clinical work is accepted.
     */
    public String compatibilityZoneSha256() {
        try {
            return zone().sha256();
        } catch (IOException invalid) {
            return CdsImportKeys.hash(
                    "CARLOS_CDS_EXPORT_INVALID_ZONE_1",
                    settings.getProperty("cds.export.clinicalZone", ""),
                    settings.getProperty("cds.import.clinicalZone", ""));
        }
    }

    CdsExportClinicalZone.Snapshot zone() throws IOException {
        var zone =
                CdsExportClinicalZone.capture(settings.getProperty("cds.export.clinicalZone", ""));
        String importZone = settings.getProperty("cds.import.clinicalZone", "").trim();
        if (!importZone.isEmpty()
                && !zone.sha256().equals(CdsExportClinicalZone.capture(importZone).sha256())) {
            throw new IOException("CDS_EXPORT_CLINICAL_ZONE_MISMATCH");
        }
        return zone;
    }

    public Plan resolve() throws IOException {
        var zone = zone();
        String build = version.get();
        if (build == null
                || build.isBlank()
                || build.length() > 128
                || !CdsExportNativeFieldAssessments.legal(build, 128)
                || build.contains("${")
                || build.codePoints().anyMatch(Character::isISOControl))
            throw new IOException("CDS_EXPORT_BUILD_VERSION_UNAVAILABLE");
        var bounds =
                new CdsExportClinicalOccurrenceService.Bounds(
                        number("maxCapturedFields", 1_000_000, 1, 100_000_000),
                        number("maxCapturedBytes", 2L << 30, 1, 128L << 30),
                        (int) number("maxIndexRows", 1_000_000, 1, 1_000_000),
                        number("maxIndexBytes", 64L << 20, 1, 64L << 20),
                        (int) number("maxNativeRows", 8192, 1, 8192),
                        (int) number("maxLookupRows", 131_072, 1, 131_072));
        retired();
        return new Plan(
                build,
                ZoneId.of(zone.zoneId()),
                zone.sha256(),
                bounds,
                CdsExportXmlLimitsDto.defaults(),
                number("retentionSeconds", 7 * 86_400L, 60, 30 * 86_400L));
    }

    /**
     * Settings that used to cap a whole package: its patients, members, on-disk inventory,
     * supporting files, the per-chart records the packaging step kept in memory, the archive, its
     * encrypted delivery and any one protected staging file. No count, size or time setting may
     * refuse, hold or fail an export, so each of these is accepted and ignored, whatever its
     * value, instead of failing an export or start-up. A roster, a member list, the inventory,
     * the per-chart packaging records and the supporting files are streamed to protected storage
     * ({@link CdsExportSpoolService}, {@link CdsExportPackageInventoryService}); the archive is
     * ZIP64 and a delivery larger than one AES-GCM message is written in parts
     * ({@link CdsExportDeliveryComposer}). Only a genuine storage failure stops an export.
     */
    static final List<String> RETIRED =
            List.of(
                    "maxPatients",
                    "maxPackageMembers",
                    "maxPackageIndexBytes",
                    "maxSupportingBytes",
                    "maxMetadataBytes",
                    "maxZipBytes",
                    "maxCiphertextBytes",
                    "maxStagedFileBytes");

    private final java.util.concurrent.atomic.AtomicBoolean retiredReported =
            new java.util.concurrent.atomic.AtomicBoolean();

    /** Names the ignored settings once per process; setting names only, never a value. */
    private void retired() {
        var present = new ArrayList<String>();
        for (String suffix : RETIRED)
            if (settings.getProperty("cds.export." + suffix) != null)
                present.add("cds.export." + suffix);
        if (!present.isEmpty() && retiredReported.compareAndSet(false, true))
            org.apache.logging.log4j.LogManager.getLogger(CdsExportActivePlanService.class)
                    .warn("CDS_EXPORT_RETIRED_SETTINGS_IGNORED settings={}", present);
    }

    private long number(String suffix, long fallback, long minimum, long maximum) {
        return number(settings, suffix, fallback, minimum, maximum);
    }

    static long number(
            Properties settings, String suffix, long fallback, long minimum, long maximum) {
        String text = settings.getProperty("cds.export." + suffix, Long.toString(fallback)).strip();
        try {
            if (!text.matches("[0-9]{1,15}")) invalid();
            long value = Long.parseLong(text);
            if (value < minimum || value > maximum) invalid();
            return value;
        } catch (NumberFormatException bad) {
            throw new IllegalStateException("CDS_EXPORT_CONFIGURATION_INVALID");
        }
    }

    private static void invalid() {
        throw new IllegalStateException("CDS_EXPORT_CONFIGURATION_INVALID");
    }
}
