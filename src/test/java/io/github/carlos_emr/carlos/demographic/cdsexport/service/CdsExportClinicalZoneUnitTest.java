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

import static org.assertj.core.api.Assertions.*;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class CdsExportClinicalZoneUnitTest {
    @Test
    void shouldVerifyExplicitRegionAndRulesVersionSurviveRecovery_forClinicalZoneContract()
            throws Exception {
        var frozen = CdsExportClinicalZone.capture("America/Toronto");
        assertThat(frozen.zoneId()).isEqualTo("America/Toronto");
        assertThat(frozen.tzdbVersion()).isNotBlank();
        assertThat(
                        CdsExportClinicalZone.restore(
                                frozen.zoneId(), frozen.tzdbVersion(), frozen.sha256()))
                .isEqualTo(ZoneId.of("America/Toronto"));
    }

    @Test
    void shouldVerifyAbsentAndInvalidSettingsNeverBecomeTheJvmDefault_forClinicalZoneContract() {
        for (String value : Arrays.asList(null, ""))
            assertThatThrownBy(() -> CdsExportClinicalZone.capture(value))
                    .hasMessage("CDS_EXPORT_CLINICAL_ZONE_REQUIRED");
        for (String value :
                List.of(" America/Toronto", "America/Toronto ", "Invalid/Unknown", "x".repeat(256)))
            assertThatThrownBy(() -> CdsExportClinicalZone.capture(value))
                    .hasMessage("CDS_EXPORT_CLINICAL_ZONE_INVALID");
    }

    @Test
    void shouldVerifyExplicitFixedOffsetHasNoMutableRegionRulesDependency_forClinicalZoneContract()
            throws Exception {
        var value = CdsExportClinicalZone.capture("+00:00");
        assertThat(value.zoneId()).isEqualTo("Z");
        assertThat(value.tzdbVersion()).isEqualTo("fixed.0");
        assertThat(
                        CdsExportClinicalZone.restore(
                                value.zoneId(), value.tzdbVersion(), value.sha256()))
                .isEqualTo(ZoneOffset.UTC);
        var prefixed = CdsExportClinicalZone.capture("UTC+05:30");
        assertThat(prefixed.tzdbVersion()).isEqualTo("fixed.19800");
        assertThat(
                        CdsExportClinicalZone.restore(
                                prefixed.zoneId(), prefixed.tzdbVersion(), prefixed.sha256()))
                .isEqualTo(ZoneId.of("UTC+05:30"));
    }

    @Test
    void shouldVerifyAChangedCapturedZoneCannotBorrowAnotherSnapshotDigest_forClinicalZoneContract()
            throws Exception {
        var frozen = CdsExportClinicalZone.capture("America/Toronto");
        assertThatThrownBy(
                        () ->
                                CdsExportClinicalZone.restore(
                                        "America/Vancouver", frozen.tzdbVersion(), frozen.sha256()))
                .hasMessage("CDS_EXPORT_CLINICAL_ZONE_INVALID");
    }

    @Test
    void
            shouldVerifyAGenuineOldVersionDigestCannotSilentlyAdoptCurrentTimezoneRules_forClinicalZoneContract()
                    throws Exception {
        String zone = "America/Toronto", version = "fixture-historic-1900";
        String digest =
                HexFormat.of()
                        .formatHex(
                                CdsImportKeys.digest()
                                        .digest(
                                                ("CDS_CLINICAL_ZONE_V1\0" + zone + "\0" + version)
                                                        .getBytes(StandardCharsets.US_ASCII)));
        assertThatThrownBy(() -> CdsExportClinicalZone.restore(zone, version, digest))
                .hasMessage("CDS_EXPORT_CLINICAL_ZONE_VERSION_REQUIRES_REVIEW");
    }

    @Test
    void
            shouldVerifyTrustedConfigurationHasAnExplicitContractAndNoPretendSqlPrimaryKey_forClinicalZoneContract()
                    throws Exception {
        var reader = new CdsExportClinicalZoneSourceReader("America/Toronto");
        assertThat(reader.count(27)).isEqualTo(1);
        assertThat(new CdsExportClinicalZoneSourceReader(" America/Toronto ").count(27))
                .isEqualTo(1);
        assertThat(reader.schema().engine()).isEqualTo("TRUSTED_CONFIGURATION");
        assertThat(reader.schema().primaryKeyColumns()).isEmpty();
        assertThat(reader.receiptColumns()).isEmpty();
        assertThat(new CdsExportSourceSchemaValidator().verify(reader.schema(), reader.columns()))
                .matches("[0-9a-f]{64}");
        assertThatThrownBy(() -> new CdsExportClinicalZoneSourceReader("").count(27))
                .isInstanceOf(java.io.UncheckedIOException.class)
                .hasMessageContaining("CDS_EXPORT_CLINICAL_ZONE_REQUIRED");
    }

    @Test
    void shouldCaptureTheSameConfiguredZone_asTheAcceptedExportPlan() throws Exception {
        var settings = new java.util.Properties();
        settings.setProperty("cds.export.clinicalZone", "America/Toronto");
        var plan = new CdsExportActivePlanService(settings, () -> "2026.08.0-test");
        var reader = new CdsExportClinicalZoneSourceReader(plan);
        assertThat(reader.count(27)).isEqualTo(1);
        assertThat(reader.categories())
                .contains(
                        io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory
                                .IMMUNIZATIONS);
        settings.setProperty("cds.import.clinicalZone", "America/Vancouver");
        var conflicting =
                new CdsExportClinicalZoneSourceReader(
                        new CdsExportActivePlanService(settings, () -> "2026.08.0-test"));
        assertThatThrownBy(() -> conflicting.count(27))
                .hasMessageContaining("CDS_EXPORT_CLINICAL_ZONE_MISMATCH");
    }
}
