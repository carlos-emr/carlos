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
import java.io.IOException;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/** Lazy active-work budgets and semantic zone binding; metadata/report recovery does not parse active settings. */
@Service
public class CdsExportActivePlanService {
    public record Plan(String applicationVersion, ZoneId clinicalZone, String zoneSha256,
            CdsExportClinicalOccurrenceService.Bounds source, CdsExportXmlLimitsDto xml,
            CdsExportClinicalPackageService.Limits archive, long ciphertextBytes, long retentionSeconds) { }
    private final Properties settings = new Properties();
    private final Supplier<String> version;
    @org.springframework.beans.factory.annotation.Autowired
    public CdsExportActivePlanService(@Qualifier("cdsExportProperties") Properties settings) { this(settings, () -> BuildInfo.getInstance().getVersion()); }
    CdsExportActivePlanService(Properties settings, Supplier<String> version) { this.settings.putAll(Objects.requireNonNull(settings)); this.version = Objects.requireNonNull(version); }
    public Plan resolve() throws IOException {
        var zone = CdsExportClinicalZone.capture(settings.getProperty("cds.export.clinicalZone", "")); String build = version.get();
        if (build == null || build.isBlank() || build.length() > 128 || !CdsExportNativeFieldAssessments.legal(build, 128) || build.contains("${")
                || build.codePoints().anyMatch(Character::isISOControl)) throw new IOException("CDS_EXPORT_BUILD_VERSION_UNAVAILABLE");
        var bounds = new CdsExportClinicalOccurrenceService.Bounds(number("maxCapturedFields", 1_000_000, 1, 100_000_000),
                number("maxCapturedBytes", 2L << 30, 1, 128L << 30), (int) number("maxIndexRows", 1_000_000, 1, 1_000_000),
                number("maxIndexBytes", 64L << 20, 1, 64L << 20), (int) number("maxNativeRows", 8192, 1, 8192), (int) number("maxLookupRows", 131_072, 1, 131_072));
        var archive = new CdsExportClinicalPackageService.Limits(number("maxPatients", 100_000, 1, 2_000_000), number("maxPackageMembers", 2_000_000, 3, 2_000_000),
                number("maxPackageIndexBytes", 1L << 30, 12, 64L << 30), number("maxMetadataBytes", 256L << 20, 1, 256L << 20),
                number("maxSupportingBytes", 64L << 20, 1, 64L << 30), number("maxZipBytes", 64L << 30, 1, 64L << 30));
        return new Plan(build, ZoneId.of(zone.zoneId()), zone.sha256(), bounds, CdsExportXmlLimitsDto.defaults(), archive,
                number("maxCiphertextBytes", 128L << 30, 1, 128L << 30), number("retentionSeconds", 7 * 86_400L, 60, 30 * 86_400L));
    }
    private long number(String suffix, long fallback, long minimum, long maximum) {
        String text = settings.getProperty("cds.export." + suffix, Long.toString(fallback)).strip();
        try { if (!text.matches("[0-9]{1,15}")) invalid(); long value = Long.parseLong(text); if (value < minimum || value > maximum) invalid(); return value; }
        catch (NumberFormatException bad) { throw new IllegalStateException("CDS_EXPORT_CONFIGURATION_INVALID"); }
    }
    private static void invalid() { throw new IllegalStateException("CDS_EXPORT_CONFIGURATION_INVALID"); }
}
