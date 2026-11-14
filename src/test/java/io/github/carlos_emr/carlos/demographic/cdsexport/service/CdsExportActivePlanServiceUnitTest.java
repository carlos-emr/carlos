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
import java.util.Properties;
import org.junit.jupiter.api.*;

@Tag("unit")
@Tag("cds-export")
class CdsExportActivePlanServiceUnitTest {
    private Properties settings() {
        var properties = new Properties();
        properties.setProperty("cds.export.clinicalZone", "America/Toronto");
        return properties;
    }

    @Test
    void shouldResolveFiniteBudgetsAndTheExactClinicZone_fromServerOwnedSettings()
            throws Exception {
        var plan = new CdsExportActivePlanService(settings(), () -> "2026.09.0-test").resolve();
        assertThat(plan.clinicalZone().getId()).isEqualTo("America/Toronto");
        assertThat(plan.source().nativeRows()).isEqualTo(8192);
        assertThat(plan.retentionSeconds()).isEqualTo(604800);
        assertThat(plan.zoneSha256()).matches("[0-9a-f]{64}");
    }

    @Test
    void shouldDeferActiveConfigurationParsing_untilClinicalWorkActuallyRequiresIt() {
        var settings = settings();
        settings.setProperty("cds.export.maxCapturedBytes", "invalid");
        var service = new CdsExportActivePlanService(settings, () -> "2026.09.0-test");
        assertThatThrownBy(service::resolve).hasMessage("CDS_EXPORT_CONFIGURATION_INVALID");
    }

    @Test
    void shouldRefuseAnUnboundedOrOverflowingBudget_insteadOfClampingIt() {
        for (String value :
                new String[] {"0", "-1", "4294967297", "999999999999999999999", "1.5"}) {
            var settings = settings();
            settings.setProperty("cds.export.retentionSeconds", value);
            assertThatThrownBy(
                            () ->
                                    new CdsExportActivePlanService(settings, () -> "2026.09.0-test")
                                            .resolve())
                    .hasMessage("CDS_EXPORT_CONFIGURATION_INVALID");
        }
    }

    @Test
    void shouldIgnoreEveryRetiredPackageCeilingSetting_whateverItsValue() throws Exception {
        for (String value : new String[] {"1", "0", "-1", "100000", "invalid", ""}) {
            var settings = settings();
            for (String retired : CdsExportActivePlanService.RETIRED)
                settings.setProperty("cds.export." + retired, value);
            try (var log =
                    io.github.carlos_emr.carlos.test.logging.LogCapture.forLogger(
                            CdsExportActivePlanService.class)) {
                var service = new CdsExportActivePlanService(settings, () -> "2026.09.0-test");
                // No setting can make a roster, a member count, the inventory, the packaging
                // records, the supporting files, the archive, its delivery or one staging file
                // too large to export: every value, valid or not, resolves to the same plan.
                assertThat(service.resolve().retentionSeconds()).isEqualTo(604800);
                service.resolve();
                // Said once, by name only, never a value.
                assertThat(log.messages())
                        .containsExactly(
                                "CDS_EXPORT_RETIRED_SETTINGS_IGNORED settings=[cds.export.maxPatients,"
                                        + " cds.export.maxPackageMembers,"
                                        + " cds.export.maxPackageIndexBytes,"
                                        + " cds.export.maxSupportingBytes,"
                                        + " cds.export.maxMetadataBytes,"
                                        + " cds.export.maxZipBytes,"
                                        + " cds.export.maxCiphertextBytes,"
                                        + " cds.export.maxStagedFileBytes]");
            }
        }
    }

    @Test
    void shouldRefuseAMissingOrUnfilteredBuildIdentity_insteadOfInventingOne() {
        for (String version : new String[] {"", "${project.version}", "version\nINJECTED"})
            assertThatThrownBy(
                            () ->
                                    new CdsExportActivePlanService(settings(), () -> version)
                                            .resolve())
                    .hasMessage("CDS_EXPORT_BUILD_VERSION_UNAVAILABLE");
    }

    @Test
    void shouldRefuseAMissingExplicitClinicZone_withoutAdoptingTheJvmDefault() {
        assertThatThrownBy(
                        () ->
                                new CdsExportActivePlanService(
                                                new Properties(), () -> "2026.09.0-test")
                                        .resolve())
                .hasMessage("CDS_EXPORT_CLINICAL_ZONE_REQUIRED");
    }

    @Test
    void shouldFreezeTheProvidedConfiguration_withoutFollowingLaterCallerMutation()
            throws Exception {
        var settings = settings();
        var service = new CdsExportActivePlanService(settings, () -> "2026.09.0-test");
        settings.setProperty("cds.export.retentionSeconds", "60");
        assertThat(service.resolve().retentionSeconds()).isEqualTo(604800);
    }

    @Test
    void shouldBindSupportingFilesAndAppointments_toTheSameConfiguredClinicZone() throws Exception {
        var settings = settings();
        settings.setProperty("cds.import.clinicalZone", " America/Toronto ");
        var service = new CdsExportActivePlanService(settings, () -> "2026.09.0-test");
        assertThat(service.resolve().clinicalZone().getId()).isEqualTo("America/Toronto");
        settings.setProperty("cds.import.clinicalZone", "America/Vancouver");
        assertThatThrownBy(
                        () ->
                                new CdsExportActivePlanService(settings, () -> "2026.09.0-test")
                                        .resolve())
                .hasMessage("CDS_EXPORT_CLINICAL_ZONE_MISMATCH");
        settings.setProperty("cds.import.clinicalZone", "invalid/zone");
        assertThatThrownBy(
                        () ->
                                new CdsExportActivePlanService(settings, () -> "2026.09.0-test")
                                        .resolve())
                .hasMessage("CDS_EXPORT_CLINICAL_ZONE_INVALID");
        assertThat(service.resolve().clinicalZone().getId()).isEqualTo("America/Toronto");
    }

    @Test
    void shouldKeepMetadataIdentityAvailable_whenActiveBudgetsOrBuildIdentityAreInvalid()
            throws Exception {
        var valid = new CdsExportActivePlanService(settings(), () -> "2026.09.0-test");
        var broken = settings();
        broken.setProperty("cds.export.maxMetadataBytes", "invalid");
        var service =
                new CdsExportActivePlanService(
                        broken,
                        () -> {
                            throw new AssertionError(
                                    "Active build lookup during metadata recovery");
                        });
        assertThat(service.compatibilityZoneSha256()).isEqualTo(valid.resolve().zoneSha256());
    }

    @Test
    void shouldKeepInvalidZoneIdentityDistinct_withoutPermittingClinicalWork() throws Exception {
        var valid = new CdsExportActivePlanService(settings(), () -> "2026.09.0-test");
        var identities = new java.util.HashSet<String>();
        identities.add(valid.compatibilityZoneSha256());
        for (String zone : new String[] {"", "invalid/zone", " America/Toronto"}) {
            var configured = settings();
            configured.setProperty("cds.export.clinicalZone", zone);
            var service = new CdsExportActivePlanService(configured, () -> "2026.09.0-test");
            assertThat(service.compatibilityZoneSha256()).matches("[0-9a-f]{64}");
            assertThat(identities.add(service.compatibilityZoneSha256())).isTrue();
            assertThatThrownBy(service::resolve).isInstanceOf(java.io.IOException.class);
        }
        var mismatch = settings();
        mismatch.setProperty("cds.import.clinicalZone", "America/Vancouver");
        var service = new CdsExportActivePlanService(mismatch, () -> "2026.09.0-test");
        assertThat(identities.add(service.compatibilityZoneSha256())).isTrue();
        assertThatThrownBy(service::resolve).hasMessage("CDS_EXPORT_CLINICAL_ZONE_MISMATCH");
    }
}
