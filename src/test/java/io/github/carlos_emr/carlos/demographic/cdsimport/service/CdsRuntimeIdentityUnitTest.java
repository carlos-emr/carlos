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
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsImportException;

import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsCalendarZone;



import io.github.carlos_emr.carlos.utility.CdsImportKeys;

import static org.assertj.core.api.Assertions.*;


import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Tag("unit")
class CdsRuntimeIdentityUnitTest {
    @TempDir Path temp;

    @Test
    void shouldHashActualBytes_whenArtifactPathOrMtimeChanges() throws Exception {
        Path first = Files.writeString(temp.resolve("first.jar"), "synthetic exact artifact bytes");
        Path folder = Files.createDirectory(temp.resolve("different-build-location"));
        Path second = Files.copy(first, folder.resolve("renamed.jar"));
        Files.setLastModifiedTime(second, FileTime.fromMillis(1));
        byte[] buffer = new byte[65536];
        String expected = CdsRuntimeIdentity.artifactHash(first, 1000, buffer);
        assertThat(CdsRuntimeIdentity.artifactHash(second, 1000, buffer)).isEqualTo(expected);
        Files.writeString(second, "synthetic changed artifact bytes");
        assertThat(CdsRuntimeIdentity.artifactHash(second, 1000, buffer)).isNotEqualTo(expected);
    }

    @Test
    void shouldRefuseRuntimeIdentity_whenArtifactOriginIsOversizedMissingOrDirectory()
            throws Exception {
        byte[] buffer = new byte[65536];
        Path file = Files.write(temp.resolve("oversized.jar"), new byte[101]);
        assertThatThrownBy(() -> CdsRuntimeIdentity.artifactHash(file, 100, buffer))
                .hasMessageContaining("IMPORT_RULESET_RUNTIME_UNAVAILABLE");
        assertThatThrownBy(
                        () ->
                                CdsRuntimeIdentity.artifactHash(
                                        temp.resolve("missing.jar"), 100, buffer))
                .hasMessageContaining("IMPORT_RULESET_RUNTIME_UNAVAILABLE");
        assertThatThrownBy(() -> CdsRuntimeIdentity.artifactHash(temp, 1000, buffer))
                .hasMessageContaining("IMPORT_RULESET_RUNTIME_UNAVAILABLE");
    }

    @Test
    void shouldRefuseRuntimeIdentity_whenActualDependencyCannotBeResolved() {
        assertThatThrownBy(
                        () ->
                                CdsRuntimeIdentity.addTo(
                                        CdsImportKeys.digest(),
                                        new ClassLoader(null) {},
                                        new byte[65536]))
                .hasMessageContaining("IMPORT_RULESET_RUNTIME_UNAVAILABLE");
    }

    @ParameterizedTest
    @ValueSource(strings={"com/google/common/base/Ascii.class","com/google/common/base/CharMatcher.class"})
    @ResourceLock("java.util.TimeZone")
    @ResourceLock("java.util.Locale")
    void shouldRefuseRuntimeIdentity_whenAnActualAsciiNormalizationDependencyIsUnavailable(String hiddenResource) {
        ClassLoader deployed=CdsRuntimeIdentity.class.getClassLoader();
        assertThat(deployed.getResource(hiddenResource)).isNotNull();
        var hiddenRequests=new AtomicInteger();
        ClassLoader unavailable=new ClassLoader(deployed) {
            @Override public java.net.URL getResource(String name) {
                if (hiddenResource.equals(name)) {hiddenRequests.incrementAndGet();return null;}
                return super.getResource(name);
            }
        };
        assertThatThrownBy(()->CdsRuntimeIdentity.addTo(CdsImportKeys.digest(),unavailable,new byte[65536]))
                .isInstanceOf(CdsImportException.class).hasMessage("IMPORT_RULESET_RUNTIME_UNAVAILABLE");
        assertThat(hiddenRequests).hasValue(1);
    }

    @Test
    void shouldHashDeterministicZoneRules_whenRuntimeIsUnchanged() throws Exception {
        String first = CdsRuntimeIdentity.zoneRulesHash();
        assertThat(first).matches("[0-9a-f]{64}");
        assertThat(CdsRuntimeIdentity.zoneRulesHash()).isEqualTo(first);
    }

    @Test
    void shouldRetryWithoutPoisoningCache_whenInitialFingerprintCalculationFails() {
        var cache = new CdsRulesetFingerprint.SuccessfulCache<String>();
        var attempts = new AtomicInteger();
        assertThatThrownBy(
                        () ->
                                cache.get(
                                        () -> {
                                            attempts.incrementAndGet();
                                            throw new CdsImportException(
                                                    "IMPORT_RULESET_RUNTIME_UNAVAILABLE");
                                        }))
                .isInstanceOf(CdsImportException.class)
                .hasMessage("IMPORT_RULESET_RUNTIME_UNAVAILABLE");
        assertThat(
                        cache.get(
                                () -> {
                                    attempts.incrementAndGet();
                                    return "complete";
                                }))
                .isEqualTo("complete");
        assertThat(
                        cache.get(
                                () -> {
                                    throw new AssertionError("successful cache recalculated");
                                }))
                .isEqualTo("complete");
        assertThat(attempts).hasValue(2);
    }

    @Test
    void shouldPublishOneCompleteValue_whenFingerprintRequestsRace() throws Exception {
        var cache = new CdsRulesetFingerprint.SuccessfulCache<java.util.List<String>>();
        var attempts = new AtomicInteger();
        var start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(8)) {
            var futures =
                    new java.util.ArrayList<java.util.concurrent.Future<java.util.List<String>>>();
            for (int i = 0; i < 16; i++) {
                futures.add(
                        workers.submit(
                                () -> {
                                    if (!start.await(5, TimeUnit.SECONDS))
                                        throw new AssertionError("cache start timed out");
                                    return cache.get(
                                            () -> {
                                                attempts.incrementAndGet();
                                                return java.util.List.of(
                                                        "implementation", "runtime");
                                            });
                                }));
            }
            start.countDown();
            var first = futures.getFirst().get(5, TimeUnit.SECONDS);
            assertThat(first).containsExactly("implementation", "runtime");
            for (var future : futures) assertThat(future.get(5, TimeUnit.SECONDS)).isSameAs(first);
        }
        assertThat(attempts).hasValue(1);
    }

    @Test
    @ResourceLock("java.util.Locale")
    void shouldRefuseFrozenRuntime_whenEitherLocaleCategoryChanges() throws Exception {
        Locale generic = Locale.getDefault();
        Locale format = Locale.getDefault(Locale.Category.FORMAT);
        Locale display = Locale.getDefault(Locale.Category.DISPLAY);
        try {
            var frozen = CdsRuntimeIdentity.snapshot();
            Locale.setDefault(
                    Locale.Category.FORMAT,
                    format.equals(Locale.FRANCE) ? Locale.JAPAN : Locale.FRANCE);
            assertThatThrownBy(frozen::verify).hasMessage("IMPORT_FROZEN_RUNTIME_CHANGED");
            Locale.setDefault(Locale.Category.FORMAT, format);
            frozen.verify();
            Locale.setDefault(
                    Locale.Category.DISPLAY,
                    display.equals(Locale.FRANCE) ? Locale.JAPAN : Locale.FRANCE);
            assertThatThrownBy(frozen::verify).hasMessage("IMPORT_FROZEN_RUNTIME_CHANGED");
        } finally {
            Locale.setDefault(generic);
            Locale.setDefault(Locale.Category.FORMAT, format);
            Locale.setDefault(Locale.Category.DISPLAY, display);
        }
    }

    @Test
    @ResourceLock("java.util.TimeZone")
    void shouldRefuseFrozenRuntime_whenDefaultTimeZoneChanges() throws Exception {
        TimeZone original = TimeZone.getDefault();
        try {
            var frozen = CdsRuntimeIdentity.snapshot();
            TimeZone.setDefault(
                    TimeZone.getTimeZone(
                            original.getID().equals("UTC") ? "America/Toronto" : "UTC"));
            assertThatThrownBy(frozen::verify).hasMessage("IMPORT_FROZEN_RUNTIME_CHANGED");
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void shouldDistinguishActualRules_whenSimpleTimeZonesShareTheSameId() {
        var utc = new java.util.SimpleTimeZone(0, "UTC");
        var shifted = new java.util.SimpleTimeZone(3_600_000, "UTC");
        assertThat(CdsCalendarZone.evidence(utc)).isNotEqualTo(CdsCalendarZone.evidence(shifted));
        var firstDst = new java.util.SimpleTimeZone(0, "UTC", 2, 1, 1, 0, 9, 1, 1, 0);
        var secondDst = new java.util.SimpleTimeZone(0, "UTC", 3, 1, 1, 0, 9, 1, 1, 0);
        assertThat(CdsCalendarZone.evidence(firstDst))
                .isNotEqualTo(CdsCalendarZone.evidence(secondDst));
    }

    @Test
    void shouldRejectUntrustedSubclass_whenCalendarEvidenceIsRequested() {
        var custom = new java.util.SimpleTimeZone(0, "UTC") {};
        assertThatThrownBy(() -> CdsCalendarZone.evidence(custom))
                .hasMessage("IMPORT_RUNTIME_TIME_ZONE_UNSUPPORTED");
    }

    @Test
    @ResourceLock("java.util.TimeZone")
    void shouldRejectSameIdWrongCalendarRules_whenImportAdmissionIsChecked() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(new java.util.SimpleTimeZone(3_600_000, "UTC"));
            for (String clinicalZone : new String[] {"", "UTC"}) {
                var properties = new java.util.Properties();
                properties.setProperty("cds.import.enabled", "true");
                properties.setProperty("cds.import.clinicalZone", clinicalZone);
                var configuration =
                        new io.github.carlos_emr.carlos.demographic.cdsimport.service
                                .CdsImportConfigurationService(properties);
                assertThatThrownBy(configuration::requireEnabled)
                        .hasMessage("IMPORT_RUNTIME_ZONE_MISMATCH");
            }
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    @ResourceLock("java.util.TimeZone")
    void shouldAcceptExactFixedOffsetRules_whenTrustedCalendarMatchesItsZoneId() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(new java.util.SimpleTimeZone(0, "UTC"));
            assertThat(CdsCalendarZone.requireCurrentCalendarRules())
                    .isEqualTo(java.time.ZoneId.of("UTC"));
            TimeZone.setDefault(TimeZone.getTimeZone("America/Toronto"));
            assertThat(CdsCalendarZone.requireCurrentCalendarRules())
                    .isEqualTo(java.time.ZoneId.of("America/Toronto"));
        } finally {
            TimeZone.setDefault(original);
        }
    }
}
