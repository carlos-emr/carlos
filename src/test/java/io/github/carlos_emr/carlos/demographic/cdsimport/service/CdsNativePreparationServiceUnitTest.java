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

import io.github.carlos_emr.carlos.commn.dao.CdsImportPreparationDao;
import io.github.carlos_emr.carlos.commn.model.CdsImportPreparation;
import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportCommand;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsLaboratoryNativeDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsTemporalDto;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@Tag("unit")
class CdsNativePreparationServiceUnitTest {
    public record Source(String text, Map<String, String> values) {}

    /** One field of each java.time shape a sealed preparation can carry. */
    public record Clock(
            java.time.Instant beforeEpoch,
            java.time.Instant recorded,
            java.time.LocalDate born,
            java.time.LocalDateTime local,
            java.time.LocalTime time,
            OffsetDateTime offset,
            java.time.YearMonth month,
            java.time.ZoneId zone,
            java.time.Duration elapsed,
            java.time.Duration correction) {}

    @Test
    void shouldKeepTheJavaTimeWireFormat_whenAPreparationIsSealedAndReadBack() throws Exception {
        // #5711 moved jackson-datatype-jsr310 from 2.17.1 to the jackson-bom version. The text a
        // preparation is sealed with must not change: ISO-8601 for dates, times and instants
        // (the fraction kept), numeric seconds for durations. 2.17 read a negative fractional
        // duration back wrong (-1.5 s as -0.5 s); the aligned module reads it exactly.
        var dao = mock(CdsImportPreparationDao.class);
        Map<String, CdsImportPreparation> rows = new HashMap<>();
        when(dao.get(anyString())).thenAnswer(call -> rows.get(call.getArgument(0)));
        doAnswer(
                        call -> {
                            CdsImportPreparation row = call.getArgument(0);
                            rows.put(row.getId(), row);
                            return null;
                        })
                .when(dao)
                .persist(any());
        var value =
                new Clock(
                        java.time.Instant.parse("1901-12-13T20:45:52.5Z"),
                        java.time.Instant.parse("2024-03-10T07:00:00.123456789Z"),
                        java.time.LocalDate.of(1950, java.time.Month.FEBRUARY, 28),
                        java.time.LocalDateTime.of(2021, java.time.Month.NOVEMBER, 7, 1, 30),
                        java.time.LocalTime.of(9, 5),
                        OffsetDateTime.parse("1883-11-18T12:00:00-05:17:32"),
                        java.time.YearMonth.of(2023, java.time.Month.JULY),
                        java.time.ZoneId.of("America/St_Johns"),
                        java.time.Duration.ofDays(30),
                        java.time.Duration.ofMillis(-1500));
        service(dao).store("run", 1, "a".repeat(64), "b".repeat(64), command(), value);
        assertThat(rows.values().iterator().next().getPayload())
                .isEqualTo(
                        "{\"beforeEpoch\":\"1901-12-13T20:45:52.500Z\","
                                + "\"recorded\":\"2024-03-10T07:00:00.123456789Z\","
                                + "\"born\":\"1950-02-28\","
                                + "\"local\":\"2021-11-07T01:30:00\","
                                + "\"time\":\"09:05:00\","
                                + "\"offset\":\"1883-11-18T12:00:00-05:17:32\","
                                + "\"month\":\"2023-07\","
                                + "\"zone\":\"America/St_Johns\","
                                + "\"elapsed\":2592000.000000000,"
                                + "\"correction\":-1.500000000}");
        try (var _ = mockStatic(CdsImportUnitService.class)) {
            var decoded =
                    service(dao)
                            .load("run", 1, "a".repeat(64), "b".repeat(64), command(), Clock.class);
            assertThat(decoded).isEqualTo(value);
            assertThat(decoded.correction()).isEqualTo(java.time.Duration.ofMillis(-1500));
            assertThat(decoded.offset().getOffset()).isEqualTo(value.offset().getOffset());
        }
    }

    @Test
    void shouldPersistAndReuseCanonicalLargeDto_whenMapOrderChangesAfterRestart() throws Exception {
        var dao = mock(CdsImportPreparationDao.class);
        Map<String, CdsImportPreparation> rows = new HashMap<>();
        when(dao.get(anyString())).thenAnswer(call -> rows.get(call.getArgument(0)));
        doAnswer(
                        call -> {
                            CdsImportPreparation row = call.getArgument(0);
                            rows.put(row.getId(), row);
                            return null;
                        })
                .when(dao)
                .persist(any());
        var service = service(dao);
        Map<String, String> first = new LinkedHashMap<>();
        first.put("b", "two");
        first.put("a", "one");
        Map<String, String> second = new LinkedHashMap<>();
        second.put("a", "one");
        second.put("b", "two");
        service.store(
                "run",
                1,
                "a".repeat(64),
                "b".repeat(64),
                command(),
                new Source("x".repeat(100000), first));
        assertThatCode(
                        () ->
                                service.store(
                                        "run",
                                        1,
                                        "a".repeat(64),
                                        "b".repeat(64),
                                        command(),
                                        new Source("x".repeat(100000), second)))
                .doesNotThrowAnyException();
        verify(dao, times(1)).persist(any());
        assertThat(rows.values().iterator().next().getPayload()).contains("x".repeat(100000));
    }

    @Test
    void shouldRegenerateOnlyIdenticalUncommittedCache_whenStoredPayloadIsCorrupt()
            throws Exception {
        var dao = mock(CdsImportPreparationDao.class);
        var ledger = mock(CdsImportLedgerService.class);
        var unit = new io.github.carlos_emr.carlos.commn.model.CdsImportUnit();
        unit.setPatientKey("patient");
        when(ledger.unit("run", "a".repeat(64))).thenReturn(unit);
        Map<String, CdsImportPreparation> rows = new HashMap<>();
        when(dao.get(anyString())).thenAnswer(call -> rows.get(call.getArgument(0)));
        doAnswer(
                        call -> {
                            CdsImportPreparation row = call.getArgument(0);
                            rows.put(row.getId(), row);
                            return null;
                        })
                .when(dao)
                .persist(any());
        var service = service(dao, ledger);
        var source = new Source("complete Unicode 😀", Map.of());
        service.store("run", 1, "a".repeat(64), "b".repeat(64), command(), source);
        var row = rows.values().iterator().next();
        String expected = row.getPayload();
        row.setPayload("corrupt");
        service.store("run", 1, "a".repeat(64), "b".repeat(64), command(), source);
        assertThat(row.getPayload()).isEqualTo(expected);
        verify(ledger).requireExpectedCheckpointFree("run", 1, "a".repeat(64));
        verify(ledger)
                .diagnostic(
                        eq("run"),
                        eq(1L),
                        eq("patient"),
                        eq("a".repeat(64)),
                        eq("INFO"),
                        eq("NATIVE_PREPARATION_CACHE_REGENERATED"),
                        eq("RECOVERY"),
                        isNull(),
                        anyString(),
                        isNull(),
                        eq(false));
        row.setOptionsDigest("c".repeat(64));
        row.setPayload("corrupt again");
        assertThatThrownBy(
                        () ->
                                service.store(
                                        "run",
                                        1,
                                        "a".repeat(64),
                                        "b".repeat(64),
                                        command(),
                                        source))
                .isInstanceOf(CdsImportException.class)
                .hasMessageContaining("NATIVE_PREPARATION_CONFLICT");
        assertThat(row.getPayload()).isEqualTo("corrupt again");
    }

    @Test
    void shouldRefuseOversizedPreparation_beforeOpeningMetadataTransaction() {
        // Larger than the payload column holds: MEDIUMTEXT, 16,777,215 bytes.
        var dao = mock(CdsImportPreparationDao.class);
        var service = service(dao);
        assertThatThrownBy(
                        () ->
                                service.store(
                                        "run",
                                        1,
                                        "a".repeat(64),
                                        "b".repeat(64),
                                        command(),
                                        new Source(
                                                "x".repeat(CdsNativePreparationService.MAXIMUM),
                                                Map.of())))
                .isInstanceOf(Exception.class)
                .hasStackTraceContaining("NATIVE_PREPARATION_CAPACITY_EXCEEDED");
        verifyNoInteractions(dao);
    }

    @Test
    void shouldStoreTheColumnsCapacity_insteadOfTheFormerOneMebibyteCheck() {
        // V1.0.59.14 checked the record at 1,048,576 bytes; V1.0.59.50 removed that check, and
        // the bound is the MEDIUMTEXT column's own capacity.
        assertThat(CdsNativePreparationService.MAXIMUM).isEqualTo((1 << 24) - 1);
    }

    @Test
    void shouldStoreAndLoadPreparation_whenItIsLargerThanTheFormerOneMebibyteCheck()
            throws Exception {
        // A note of over 2 MiB is prepared whole and read back exactly: the record's own size,
        // not a note setting or the former 1 MiB check, decides whether it fits.
        var dao = mock(CdsImportPreparationDao.class);
        Map<String, CdsImportPreparation> rows = new HashMap<>();
        when(dao.get(anyString())).thenAnswer(call -> rows.get(call.getArgument(0)));
        doAnswer(
                        call -> {
                            CdsImportPreparation row = call.getArgument(0);
                            rows.put(row.getId(), row);
                            return null;
                        })
                .when(dao)
                .persist(any());
        var service = service(dao);
        String text = "Synthetic note line \u00e9 \ud83d\ude00\n".repeat(80_000);
        service.store(
                "run", 1, "a".repeat(64), "b".repeat(64), command(), new Source(text, Map.of()));
        byte[] payload =
                rows.values()
                        .iterator()
                        .next()
                        .getPayload()
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(payload)
                .hasSizeGreaterThan(2 << 20)
                .hasSizeLessThanOrEqualTo(CdsNativePreparationService.MAXIMUM);
        try (var active = mockStatic(CdsImportUnitService.class)) {
            assertThat(
                            service.load(
                                            "run",
                                            1,
                                            "a".repeat(64),
                                            "b".repeat(64),
                                            command(),
                                            Source.class)
                                    .text())
                    .isEqualTo(text);
        }
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"2025-03-04T10:20:30.125-05:00", "-0004-12-31T23:59:59.123456789+05:45"})
    void shouldPreserveExactSourceOffsetAndFraction_whenNativePreparationIsDecodedAfterRestart(
            String original) throws Exception {
        var dao = mock(CdsImportPreparationDao.class);
        Map<String, CdsImportPreparation> rows = new HashMap<>();
        when(dao.get(anyString())).thenAnswer(call -> rows.get(call.getArgument(0)));
        doAnswer(
                        call -> {
                            CdsImportPreparation row = call.getArgument(0);
                            rows.put(row.getId(), row);
                            return null;
                        })
                .when(dao)
                .persist(any());
        OffsetDateTime clock = OffsetDateTime.parse(original);
        var temporal =
                new CdsTemporalDto(
                        CdsTemporalDto.Precision.OFFSET_DATE_TIME,
                        original,
                        clock.toLocalDate(),
                        clock.toLocalDate(),
                        clock.toLocalDateTime(),
                        clock);
        var source =
                new CdsLaboratoryNativeDto(
                        Map.of("ResultStatus", "Final"), temporal, CdsTemporalDto.unknown());
        service(dao).store("run", 1, "a".repeat(64), "b".repeat(64), command(), source);
        // Only the unit ownership boundary is mocked; exercise the real persisted
        // JSON, digest verification and decode with a fresh service instance.
        try (var ownership = mockStatic(CdsImportUnitService.class)) {
            var decoded =
                    service(dao)
                            .load(
                                    "run",
                                    1,
                                    "a".repeat(64),
                                    "b".repeat(64),
                                    command(),
                                    CdsLaboratoryNativeDto.class);
            assertThat(decoded).isEqualTo(source);
            assertThat(decoded.collection().offsetDateTime().getOffset())
                    .isEqualTo(clock.getOffset());
            assertThat(decoded.collection().offsetDateTime().getNano()).isEqualTo(clock.getNano());
            assertThat(decoded.collection().original()).isEqualTo(original);
        }
    }

    @Test
    void shouldKeepLegacyAllergyPayloadBytes_whenStrictlyDecodingTheOptionalArchiveExtension()
            throws Exception {
        for (boolean archived : new boolean[] {false, true})
            for (boolean extensions : new boolean[] {false, true}) {
                var dao = mock(CdsImportPreparationDao.class);
                Map<String, CdsImportPreparation> rows = new HashMap<>();
                when(dao.get(anyString())).thenAnswer(call -> rows.get(call.getArgument(0)));
                doAnswer(
                                call -> {
                                    CdsImportPreparation row = call.getArgument(0);
                                    rows.put(row.getId(), row);
                                    return null;
                                })
                        .when(dao)
                        .persist(any());
                var source =
                        new io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsAllergyDto(
                                "Synthetic archive agent",
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                CdsTemporalDto.unknown(),
                                CdsTemporalDto.unknown(),
                                archived,
                                extensions ? "0012" : null,
                                extensions ? "2" : null);
                service(dao).store("run", 1, "a".repeat(64), "b".repeat(64), command(), source);
                var row = rows.values().iterator().next();
                String payload = row.getPayload(), digest = row.getDigest();
                assertThat(
                                new com.fasterxml.jackson.databind.ObjectMapper()
                                        .readTree(payload)
                                        .has("archived"))
                        .isEqualTo(archived);
                assertThat(
                                new com.fasterxml.jackson.databind.ObjectMapper()
                                        .readTree(payload)
                                        .has("ageOfOnset"))
                        .isEqualTo(extensions);
                assertThat(
                                new com.fasterxml.jackson.databind.ObjectMapper()
                                        .readTree(payload)
                                        .has("onsetOfReaction"))
                        .isEqualTo(extensions);
                try (var ownership = mockStatic(CdsImportUnitService.class)) {
                    var decoded =
                            service(dao)
                                    .load(
                                            "run",
                                            1,
                                            "a".repeat(64),
                                            "b".repeat(64),
                                            command(),
                                            io.github.carlos_emr.carlos.demographic.cdsimport.dto
                                                    .CdsAllergyDto.class);
                    assertThat(decoded).isEqualTo(source);
                    assertThat(decoded.archived()).isEqualTo(archived);
                }
                assertThat(row.getPayload()).isEqualTo(payload);
                assertThat(row.getDigest()).isEqualTo(digest);
            }
    }

    @Test void shouldLoadLegacyHistoryDefaults_withoutChangingOrRestampingTheVerifiedPayload()throws Exception {
        for(boolean archived:new boolean[]{false,true}) {
            var dao=mock(CdsImportPreparationDao.class);
            Map<String,CdsImportPreparation> rows=new HashMap<>();
            when(dao.get(anyString())).thenAnswer(call->rows.get(call.getArgument(0)));
            doAnswer(call->{CdsImportPreparation row=call.getArgument(0);rows.put(row.getId(),row);return null;})
                    .when(dao).persist(any());
            var source=new io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsHistoryNativeDto(
                    "Original history", "FamHistory",null,null,null,CdsTemporalDto.unknown(),"StartDate",java.util.List.of(),archived);
            service(dao).store("run",1,"a".repeat(64),"b".repeat(64),command(),source);
            var row=rows.values().iterator().next();String payload=row.getPayload(),digest=row.getDigest();
            assertThat(new com.fasterxml.jackson.databind.ObjectMapper().readTree(payload).has("archived")).isEqualTo(archived);
            try(var ownership=mockStatic(CdsImportUnitService.class)) {
                var decoded=service(dao).load("run",1,"a".repeat(64),"b".repeat(64),command(),
                        io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsHistoryNativeDto.class);
                assertThat(decoded).isEqualTo(source);
            }
            assertThat(row.getPayload()).isEqualTo(payload);assertThat(row.getDigest()).isEqualTo(digest);
        }
    }

    @Test
    void shouldSealAndLoadAnImmunization_withAndWithoutRestoredSourceContext() throws Exception {
        // #6198: an unrestored vaccine keeps its earlier payload bytes (no new keys) and loads
        // through the transient defaults; a restored one seals and loads its values exactly.
        for (var restored : new String[][] {{null, null}, {"FAKE-Other  clinician", "2025-02-28"}}) {
            var dao = mock(CdsImportPreparationDao.class);
            Map<String, CdsImportPreparation> rows = new HashMap<>();
            when(dao.get(anyString())).thenAnswer(call -> rows.get(call.getArgument(0)));
            doAnswer(
                            call -> {
                                CdsImportPreparation row = call.getArgument(0);
                                rows.put(row.getId(), row);
                                return null;
                            })
                    .when(dao)
                    .persist(any());
            var source =
                    new io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImmunizationDto(
                            "Synthetic vaccine",
                            "Td",
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            false,
                            null,
                            null,
                            CdsTemporalDto.unknown(),
                            restored[0],
                            restored[1]);
            service(dao).store("run", 1, "a".repeat(64), "b".repeat(64), command(), source);
            var row = rows.values().iterator().next();
            String payload = row.getPayload();
            assertThat(payload.contains("providerName")).isEqualTo(restored[0] != null);
            assertThat(payload.contains("reminderDate")).isEqualTo(restored[1] != null);
            try (var ownership = mockStatic(CdsImportUnitService.class)) {
                var decoded =
                        service(dao)
                                .load(
                                        "run",
                                        1,
                                        "a".repeat(64),
                                        "b".repeat(64),
                                        command(),
                                        io.github.carlos_emr.carlos.demographic.cdsimport.dto
                                                .CdsImmunizationDto.class);
                assertThat(decoded).isEqualTo(source);
            }
            assertThat(row.getPayload()).isEqualTo(payload);
        }
    }

    @Test
    void shouldLoadAMedicationSealedWithDurationDays_asItsExactDayDuration() throws Exception {
        // Review M4: a payload sealed before #3958 names durationDays; its meaning is unit D.
        var dao = mock(CdsImportPreparationDao.class);
        Map<String, CdsImportPreparation> rows = new HashMap<>();
        when(dao.get(anyString())).thenAnswer(call -> rows.get(call.getArgument(0)));
        doAnswer(
                        call -> {
                            CdsImportPreparation row = call.getArgument(0);
                            rows.put(row.getId(), row);
                            return null;
                        })
                .when(dao)
                .persist(any());
        String legacy =
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree(
                                getClass()
                                        .getResourceAsStream(
                                                "/cds-export/legacy-medication-preparation-duration-days.json"))
                        .get("payloads")
                        .get("days")
                        .textValue();
        var type = io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsMedicationNativeDto.class;
        var current =
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .registerModule(
                                new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                        .readValue(
                                legacy.replace(
                                        "\"durationDays\":\"030\"",
                                        "\"duration\":\"030\",\"durationUnit\":\"D\""),
                                type);
        service(dao).store("run", 1, "a".repeat(64), "b".repeat(64), command(), current);
        var row = rows.values().iterator().next();
        row.setPayload(legacy);
        row.setDigest(
                io.github.carlos_emr.carlos.demographic.cds.contract.CdsPreparationSeal.digest(
                        row.getPayloadType(),
                        row.getSourceDigest(),
                        row.getOptionsDigest(),
                        row.getRulesetDigest(),
                        legacy));
        try (var ownership = mockStatic(CdsImportUnitService.class)) {
            var decoded =
                    service(dao).load("run", 1, "a".repeat(64), "b".repeat(64), command(), type);
            assertThat(decoded).isEqualTo(current);
            assertThat(decoded.source().duration()).isEqualTo("030");
            assertThat(decoded.source().durationUnit()).isEqualTo("D");
        }
        assertThat(row.getPayload()).isEqualTo(legacy);
    }

    private static CdsNativePreparationService service(CdsImportPreparationDao dao) {
        return service(dao, mock(CdsImportLedgerService.class));
    }

    private static CdsNativePreparationService service(
            CdsImportPreparationDao dao, CdsImportLedgerService ledger) {
        var manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus(true));
        return new CdsNativePreparationService(
                ledger, dao, manager, new CdsImportConfigurationService(new Properties()));
    }

    private static CdsImportCommand command() {
        return new CdsImportCommand(
                Path.of("/source/one.xml"),
                "request",
                "1",
                null,
                null,
                "test",
                "omd-5.2",
                false,
                false,
                false,
                0,
                null,
                false);
    }
}
