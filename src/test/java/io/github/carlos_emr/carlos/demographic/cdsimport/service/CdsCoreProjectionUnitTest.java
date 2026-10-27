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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.DemographicExt;
import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportCommand;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.*;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

/** Real pinned-schema/parser values prove bounded native projection decisions independently of synthetic mock descriptors. */
class CdsCoreProjectionUnitTest extends CarlosUnitTestBase {
    @TempDir Path temporary;
    private final AtomicInteger sequence = new AtomicInteger();
    private CdsFieldParser fields;
    private CdsNativePreparationService preparation;
    private CdsProviderResolver providers;
    private CdsDemographicPersister demographics;
    private CdsHistoryPersister histories;
    private CdsAppointmentPersister appointments;
    private CdsImportCommand command;
    private CdsPatientIdentityResolver identity;

    @BeforeEach
    void prepare() throws Exception {
        fields = new CdsFieldParser();
        preparation = mock(CdsNativePreparationService.class);
        providers = mock(CdsProviderResolver.class);
        identity = mock(CdsPatientIdentityResolver.class);
        when(providers.resolve(any(), any(), any(), any(), anyBoolean()))
                .thenReturn(
                        new CdsProviderMatchDto(
                                null, CdsProviderMatchDto.Outcome.SOURCE_IDENTITY_ABSENT));
        when(identity.source(any(), any()))
                .thenReturn(
                        new CdsPatientIdentityDto(
                                "owned",
                                "Import",
                                "Fixture",
                                LocalDate.of(1971, 3, 4),
                                null,
                                null,
                                "F",
                                "AC"));
        Properties properties = new Properties();
        properties.setProperty("cds.import.clinicalZone", "America/Toronto");
        properties.setProperty("cds.import.native.maxNoteBytes", "262144");
        var configuration = new CdsImportConfigurationService(properties);
        var normalization = new CdsClinicalNormalizationService(fields);
        demographics =
                new CdsDemographicPersister(
                        fields,
                        identity,
                        providers,
                        preparation,
                        new CdsDemographicDetailsService(fields, providers),
                        new CdsDemographicRelationsService(fields));
        histories =
                new CdsHistoryPersister(
                        fields, normalization, configuration, providers, preparation);
        appointments =
                new CdsAppointmentPersister(
                        fields, normalization, configuration, providers, preparation);
        command =
                new CdsImportCommand(
                        temporary,
                        "owned-submission",
                        "999998",
                        null,
                        null,
                        "clinic",
                        "pcon-2024-02",
                        false,
                        false,
                        true,
                        0,
                        null,
                        false);
    }

    @Test
    void shouldMapEachUniqueAddressType_andKeepCanonicalProvinceWithoutSelectingFirstOccurrence()
            throws Exception {
        var record =
                parse(
                        "",
                        address("R", "Residential", "Ottawa") + address("M", "Mailing", "Toronto"));
        demographics.prepare("run", 1, "unit", record, command);
        var dto = captured(CdsDemographicNativeDto.class);
        assertThat(dto.mailing().address()).isEqualTo("Mailing");
        assertThat(dto.mailing().city()).isEqualTo("Toronto");
        assertThat(dto.residential().address()).isEqualTo("Residential");
        assertThat(dto.residential().province()).isEqualTo("ON");
        assertThat(dto.diagnostics())
                .extracting(CdsClinicalDiagnosticDto::code)
                .contains("CDS_CANADIAN_PROVINCE_NORMALIZED");
    }

    @Test
    void shouldPreserveUnknownAndNonCanadianSubdivisionCodes_withoutGuessingCanadianAliases()
            throws Exception {
        for (String subdivision : List.of("CA-XX", "US-NY", "ON")) {
            clearInvocations(preparation);
            var record =
                    parse("", address("M", "Mailing", "Toronto").replace("CA-ON", subdivision));
            demographics.prepare("run", 1, "unit", record, command);
            var dto = captured(CdsDemographicNativeDto.class);
            assertThat(dto.mailing().province()).isEqualTo(subdivision);
            assertThat(dto.diagnostics())
                    .extracting(CdsClinicalDiagnosticDto::code)
                    .doesNotContain("CDS_CANADIAN_PROVINCE_NORMALIZED");
        }
    }

    @Test
    void shouldRetainAmbiguousRepeatedAddressesInSourceView_insteadOfChoosingArbitraryFirst()
            throws Exception {
        var record = parse("", address("M", "First", "Toronto") + address("M", "Second", "Ottawa"));
        demographics.prepare("run", 1, "unit", record, command);
        var dto = captured(CdsDemographicNativeDto.class);
        assertThat(dto.mailing()).isNull();
        assertThat(dto.diagnostics())
                .extracting(CdsClinicalDiagnosticDto::code)
                .contains("CDS_REPEATED_NATIVE_VALUE_AMBIGUOUS");
    }

    @Test
    void shouldRepresentFullSmallFormattedAddress_andWarnWithoutReadingUnboundedFormattedValue()
            throws Exception {
        String large =
                "<Address addressType='M'><cdsd:Formatted>"
                        + "x".repeat(300000)
                        + "</cdsd:Formatted></Address>";
        var record = parse("", large);
        demographics.prepare("run", 1, "unit", record, command);
        var dto = captured(CdsDemographicNativeDto.class);
        assertThat(dto.mailing().address()).isNull();
        assertThat(dto.diagnostics())
                .extracting(CdsClinicalDiagnosticDto::code)
                .contains("CDS_NATIVE_SCALAR_SOURCE_ONLY");
        assertThat(fields.firstText(record, "Address/Formatted", 300001)).hasSize(300000);
    }

    @Test
    void shouldAssessRepeatedAddressAmbiguityAgainstAcceptedLogicalCorrections_notRawLexicalHashes()
            throws Exception {
        var raw = parse("", address("M", "First", "Toronto") + address("M", "Second", "Toronto"));
        List<CdsFieldDto> found = new ArrayList<>();
        fields.forEachField(
                raw,
                f -> {
                    if (f.path().equals("Address[2]/Structured[1]/Line1[1]")) found.add(f);
                });
        var corrected = corrected(raw, found.get(0), "First");
        demographics.prepare("run", 1, "unit", corrected, command);
        assertThat(captured(CdsDemographicNativeDto.class).mailing().address()).isEqualTo("First");
        assertThat(fields.readText(raw, found.get(0), 50)).isEqualTo("Second");
    }

    @Test
    void shouldKeepIdenticalRepeatedAddressMeaning_andRecordNormalization() throws Exception {
        String address = address("M", "Same", "Toronto");
        demographics.prepare("run", 1, "unit", parse("", address + address), command);
        var dto = captured(CdsDemographicNativeDto.class);
        assertThat(dto.mailing().address()).isEqualTo("Same");
        assertThat(dto.diagnostics())
                .extracting(CdsClinicalDiagnosticDto::code)
                .contains("CDS_REPEATED_IDENTICAL_NATIVE_VALUE");
    }

    @Test
    void shouldMapUnambiguousPhoneTypesAndExtensions_withoutForcingOverflowIntoNativeColumns()
            throws Exception {
        String phones =
                "<PhoneNumber"
                    + " phoneNumberType='R'><cdsd:phoneNumber>613-555-0101</cdsd:phoneNumber><cdsd:extension>12</cdsd:extension></PhoneNumber><PhoneNumber"
                    + " phoneNumberType='W'><cdsd:phoneNumber>613-555-0102</cdsd:phoneNumber></PhoneNumber><PhoneNumber"
                    + " phoneNumberType='C'><cdsd:phoneNumber>1234567890123456789012345</cdsd:phoneNumber></PhoneNumber>";
        demographics.prepare("run", 1, "unit", parse("", phones), command);
        var dto = captured(CdsDemographicNativeDto.class);
        assertThat(dto.home().number()).isEqualTo("613-555-0101");
        assertThat(dto.home().extension()).isEqualTo("12");
        assertThat(dto.work().number()).isEqualTo("613-555-0102");
        assertThat(dto.cell()).isNull();
        assertThat(dto.diagnostics())
                .extracting(CdsClinicalDiagnosticDto::code)
                .contains("CDS_PHONE_NUMBER_SOURCE_ONLY");
    }

    @Test
    void shouldCarryExactProviderResolution_withoutUsingImportActorAsAuthor() throws Exception {
        when(providers.resolve(any(), any(), any(), any(), anyBoolean()))
                .thenReturn(
                        new CdsProviderMatchDto("77", CdsProviderMatchDto.Outcome.MATCHED_NAME));
        var record = parse("", "");
        demographics.prepare("run", 1, "unit", record, command);
        assertThat(captured(CdsDemographicNativeDto.class).primaryProvider()).isEqualTo("77");
    }

    @Test
    void shouldPreserveNameWhitespaceBeforeExactProviderLookup_inEveryCoreCategory()
            throws Exception {
        String name =
                "<Name><cdsd:FirstName>Anna  Marie</cdsd:FirstName><cdsd:LastName>Doctor"
                        + " </cdsd:LastName></Name>";
        demographics.prepare(
                "run",
                1,
                "unit",
                parse("", "<PrimaryPhysician>" + name + "</PrimaryPhysician>"),
                command);
        histories.prepare(
                "run",
                1,
                "unit",
                section(
                        "ClinicalNotes",
                        "<MyClinicalNotesContent>Note</MyClinicalNotesContent><ParticipatingProviders>"
                                + name
                                + "<DateTimeNoteCreated><cdsd:FullDateTime>2026-03-04T10:00:00</cdsd:FullDateTime></DateTimeNoteCreated></ParticipatingProviders>"),
                command);
        appointments.prepare(
                "run",
                1,
                "unit",
                appointment(
                        "10:00:00",
                        "30",
                        "<cdsd:FullDate>2026-03-04</cdsd:FullDate>",
                        "<Provider>" + name + "</Provider>"),
                command);
        verify(providers, times(3)).resolve(null, null, "Anna  Marie", "Doctor ", true);
    }

    @Test
    void shouldCombineOnlyUnambiguousCompleteTelephoneComponents_withoutDuplicatedExchange()
            throws Exception {
        assertThat(
                        io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsPhoneComponents
                                .combine("613", "555", "0142"))
                .isEqualTo("613-555-0142");
        assertThat(
                        io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsPhoneComponents
                                .combine("613", "555", "5550142"))
                .isEqualTo("613-555-0142");
        assertThat(
                        io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsPhoneComponents
                                .combine("613", "999", "5550142"))
                .isNull();
        assertThat(
                        io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsPhoneComponents
                                .combine("613", null, "0142"))
                .isNull();
        var record =
                parse(
                        "",
                        "<PhoneNumber"
                            + " phoneNumberType='R'><cdsd:areaCode>613</cdsd:areaCode><cdsd:number>5550142</cdsd:number><cdsd:exchange>555</cdsd:exchange></PhoneNumber>");
        demographics.prepare("run", 1, "unit", record, command);
        assertThat(captured(CdsDemographicNativeDto.class).home().number())
                .isEqualTo("613-555-0142");
    }

    @Test
    void shouldNeverAssemblePhoneFromAnOverflowedOrMissingConstituent() throws Exception {
        var record =
                parse(
                        "",
                        "<PhoneNumber phoneNumberType='R'><cdsd:areaCode>"
                                + "1".repeat(26)
                                + "</cdsd:areaCode><cdsd:number>5550142</cdsd:number></PhoneNumber>");
        demographics.prepare("run", 1, "unit", record, command);
        var dto = captured(CdsDemographicNativeDto.class);
        assertThat(dto.home()).isNull();
        assertThat(dto.diagnostics())
                .extracting(CdsClinicalDiagnosticDto::code)
                .contains("CDS_NATIVE_SCALAR_SOURCE_ONLY");
    }

    @Test
    void shouldPreserveFullClinicalNoteBeyondOld64kLimit_withoutClipping() throws Exception {
        String text = "N".repeat(100000);
        var record =
                section(
                        "ClinicalNotes",
                        "<MyClinicalNotesContent>" + text + "</MyClinicalNotesContent>");
        histories.prepare("run", 1, "unit", record, command);
        var dto = captured(CdsHistoryNativeDto.class);
        assertThat(dto.content()).isEqualTo(text);
    }

    @Test
    void shouldUseVisibleSourceMarker_forOversizedNarrativeAndKeepOriginalDownloadable()
            throws Exception {
        String text = "N".repeat(400000);
        var record =
                section(
                        "ClinicalNotes",
                        "<MyClinicalNotesContent>" + text + "</MyClinicalNotesContent>");
        histories.prepare("run", 1, "unit", record, command);
        var dto = captured(CdsHistoryNativeDto.class);
        assertThat(dto.content())
                .contains("Read the complete original", "No narrative excerpt")
                .hasSizeLessThan(1000);
        assertThat(dto.diagnostics())
                .extracting(CdsClinicalDiagnosticDto::code)
                .contains("CDS_NOTE_COMPLETE_TEXT_SOURCE_VIEW");
        assertThat(fields.firstText(record, "MyClinicalNotesContent", 400001)).isEqualTo(text);
    }

    @Test
    void shouldApplyAuditedLogicalNoteCorrectionWithoutMistakingLargeOriginalForEffectiveLength()
            throws Exception {
        var raw =
                section(
                        "ClinicalNotes",
                        "<MyClinicalNotesContent>"
                                + "N".repeat(400000)
                                + "</MyClinicalNotesContent>");
        List<CdsFieldDto> descriptors = new ArrayList<>();
        fields.forEachField(
                raw,
                field -> {
                    if (CdsFieldParser.unindexed(field.path()).equals("MyClinicalNotesContent"))
                        descriptors.add(field);
                });
        var field = descriptors.get(0);
        String replacement = "Complete corrected source note";
        var corrected = corrected(raw, field, replacement);
        histories.prepare("run", 1, "unit", corrected, command);
        assertThat(captured(CdsHistoryNativeDto.class).content()).isEqualTo(replacement);
        verify(preparation)
                .store(
                        eq("run"),
                        eq(1L),
                        eq("unit"),
                        eq(corrected.effectiveSha256()),
                        eq(command),
                        any());
        assertThat(fields.readText(raw, field, 400001)).hasSize(400000);
        assertThat(corrected.effectiveSha256()).isNotEqualTo(raw.sha256());
    }

    @Test
    void shouldLeavePartialNoteTimestampUnknown_andKeepPrecisionNotice() throws Exception {
        var record =
                section(
                        "ClinicalNotes",
                        "<MyClinicalNotesContent>Complete source"
                            + " note</MyClinicalNotesContent><EventDateTime><cdsd:YearOnly>2020</cdsd:YearOnly></EventDateTime>");
        histories.prepare("run", 1, "unit", record, command);
        var dto = captured(CdsHistoryNativeDto.class);
        assertThat(dto.observationMillis()).isNull();
        assertThat(dto.diagnostics())
                .extracting(CdsClinicalDiagnosticDto::code)
                .contains("CDS_PARTIAL_DATE_RETAINED");
    }

    @Test
    void shouldKeepDateOnlyNoteWithoutAnInventedMidnightTimestamp() throws Exception {
        var record =
                section(
                        "ClinicalNotes",
                        "<MyClinicalNotesContent>Day-only source"
                            + " note</MyClinicalNotesContent><EventDateTime><cdsd:FullDate>2026-02-03</cdsd:FullDate></EventDateTime>");
        histories.prepare("run", 1, "unit", record, command);
        var dto = captured(CdsHistoryNativeDto.class);
        assertThat(dto.observationMillis()).isNull();
        assertThat(dto.diagnostics())
                .extracting(CdsClinicalDiagnosticDto::code)
                .contains("CDS_NOTE_DATE_ONLY_SOURCE_VIEW");
    }

    @Test
    void shouldRetainPartialAppointmentAsTypedSource_withExplicitCalendarOmission()
            throws Exception {
        var record = appointment("10:00:00", "30", "<cdsd:YearMonth>2026-03</cdsd:YearMonth>", "");
        appointments.prepare("run", 1, "unit", record, command);
        var dto = captured(CdsAppointmentNativeDto.class);
        assertThat(dto.representable()).isFalse();
        assertThat(dto.diagnostics())
                .extracting(CdsClinicalDiagnosticDto::code)
                .contains("CDS_APPOINTMENT_PARTIAL_DATE_SOURCE_ONLY");
    }

    @Test
    void shouldNeverWrapCrossMidnightIntoWrongDayCalendarSlot() throws Exception {
        var record = appointment("23:45:00", "30", "<cdsd:FullDate>2026-03-04</cdsd:FullDate>", "");
        appointments.prepare("run", 1, "unit", record, command);
        var dto = captured(CdsAppointmentNativeDto.class);
        assertThat(dto.representable()).isFalse();
        assertThat(dto.diagnostics())
                .extracting(CdsClinicalDiagnosticDto::code)
                .contains("CDS_APPOINTMENT_CROSS_MIDNIGHT_SOURCE_ONLY");
    }

    @Test
    void shouldNotInventMissingDuration_orCollapseMultidayDuration() throws Exception {
        var record = appointment("10:00:00", null, "<cdsd:FullDate>2026-03-04</cdsd:FullDate>", "");
        appointments.prepare("run", 1, "unit", record, command);
        assertThat(captured(CdsAppointmentNativeDto.class).representable()).isFalse();
        clearInvocations(preparation);
        record =
                appointment(
                        "10:00:00",
                        "9999999999999999999999999999999999999999999999999",
                        "<cdsd:FullDate>2026-03-04</cdsd:FullDate>",
                        "");
        appointments.prepare("run", 1, "unit", record, command);
        assertThat(captured(CdsAppointmentNativeDto.class).representable()).isFalse();
    }

    @Test
    void shouldPreserveFull250CharacterAppointmentStatus_and32kUnicodeNotes() throws Exception {
        String status = "S".repeat(250), notes = "é".repeat(32768);
        var record =
                appointment(
                        "10:00:00",
                        "30",
                        "<cdsd:FullDate>2026-03-04</cdsd:FullDate>",
                        "<AppointmentStatus>" + status + "</AppointmentStatus>",
                        "<AppointmentNotes>" + notes + "</AppointmentNotes>");
        appointments.prepare("run", 1, "unit", record, command);
        var dto = captured(CdsAppointmentNativeDto.class);
        assertThat(dto.representable()).isTrue();
        assertThat(dto.status()).isEqualTo(status);
        assertThat(dto.notes()).isEqualTo(notes);
    }

    @Test
    void shouldNotInventLocalTiming_forDSTGapOrAmbiguousOffsetFold() throws Exception {
        var record = appointment("02:15:00", "30", "<cdsd:FullDate>2026-03-08</cdsd:FullDate>", "");
        appointments.prepare("run", 1, "unit", record, command);
        assertThat(captured(CdsAppointmentNativeDto.class).representable()).isFalse();
        clearInvocations(preparation);
        record = appointment("01:30:00", "120", "<cdsd:FullDate>2026-03-08</cdsd:FullDate>", "");
        appointments.prepare("run", 1, "unit", record, command);
        var dto = captured(CdsAppointmentNativeDto.class);
        assertThat(dto.representable()).isFalse();
        assertThat(dto.diagnostics())
                .extracting(CdsClinicalDiagnosticDto::code)
                .contains("CDS_APPOINTMENT_DST_DURATION_SOURCE_ONLY");
    }

    @Test
    void shouldRequireConfiguredClinicZoneBeforeClaimingUnambiguousCalendarTiming()
            throws Exception {
        var unconfigured =
                new CdsAppointmentPersister(
                        fields,
                        new CdsClinicalNormalizationService(fields),
                        new CdsImportConfigurationService(new Properties()),
                        providers,
                        preparation);
        unconfigured.prepare(
                "run",
                1,
                "unit",
                appointment("10:00:00", "30", "<cdsd:FullDate>2026-03-04</cdsd:FullDate>", ""),
                command);
        var dto = captured(CdsAppointmentNativeDto.class);
        assertThat(dto.representable()).isFalse();
        assertThat(dto.diagnostics())
                .extracting(CdsClinicalDiagnosticDto::code)
                .contains("CDS_APPOINTMENT_TIME_ZONE_SOURCE_ONLY");
    }

    @Test
    void shouldRetainLongAppointmentPurposeAsCompleteSource_withVisibleNativeMarker()
            throws Exception {
        String purpose = "P".repeat(250);
        var record =
                appointment(
                        "10:00:00",
                        "30",
                        "<cdsd:FullDate>2026-03-04</cdsd:FullDate>",
                        "<AppointmentPurpose>" + purpose + "</AppointmentPurpose>");
        appointments.prepare("run", 1, "unit", record, command);
        var dto = captured(CdsAppointmentNativeDto.class);
        assertThat(dto.purpose())
                .contains("complete appointment purpose")
                .hasSizeLessThanOrEqualTo(80);
        assertThat(fields.firstText(record, "AppointmentPurpose", 250)).isEqualTo(purpose);
        assertThat(dto.diagnostics())
                .extracting(CdsClinicalDiagnosticDto::code)
                .contains("CDS_APPOINTMENT_PURPOSE_SOURCE_VIEW");
    }

    @Test
    void shouldLoadOnlyBoundedPreparedDTOInsideCalendarWrite_withoutAnyRawSourceScan()
            throws Exception {
        var dto =
                new CdsAppointmentNativeDto(
                        "2026-03-04",
                        "10:00",
                        "10:30",
                        "Source full status",
                        "Full purpose",
                        "Notes",
                        "77",
                        true,
                        List.of());
        var record = appointment("10:00:00", "30", "<cdsd:FullDate>2026-03-04</cdsd:FullDate>", "");
        Files.delete(record.fieldsFile());
        Files.delete(record.valuesFile());
        when(preparation.load(
                        "run", 1, "unit", record.sha256(), command, CdsAppointmentNativeDto.class))
                .thenReturn(dto);
        EntityManager em = mock(EntityManager.class);
        doAnswer(
                        call -> {
                            ((Appointment) call.getArgument(0)).setId(9);
                            return null;
                        })
                .when(em)
                .persist(any(Appointment.class));
        ReflectionTestUtils.setField(appointments, "entityManager", em);
        assertThat(appointments.write("run", 1, "unit", 42, record, command, n -> {}))
                .isEqualTo(java.util.Map.of("appointment", "9"));
        var captor = ArgumentCaptor.forClass(Appointment.class);
        verify(em).persist(captor.capture());
        assertThat(captor.getValue().getProviderNo()).isEqualTo("77");
        assertThat(captor.getValue().getImportedStatus()).isEqualTo(dto.status());
    }

    @Test
    void
            shouldWritePreparedDemographicAndNativePhoneExtensions_withoutActorSubstitutionOrSourceRescan()
                    throws Exception {
        var dto =
                new CdsDemographicNativeDto(
                        "owned",
                        "Import",
                        "Fixture",
                        "1971-03-04",
                        null,
                        null,
                        "F",
                        "AC",
                        java.util.Map.of(),
                        new CdsDemographicNativeDto.Address("Mailing", "Toronto", "ON", "K1A0B1"),
                        null,
                        new CdsDemographicNativeDto.Phone("613-555-0101", "12"),
                        new CdsDemographicNativeDto.Phone("613-555-0102", "23"),
                        new CdsDemographicNativeDto.Phone("613-555-0103", null),
                        "77",
                        List.of());
        var record = parse("", "");
        Files.delete(record.fieldsFile());
        Files.delete(record.valuesFile());
        when(preparation.load(
                        "run", 1, "unit", record.sha256(), command, CdsDemographicNativeDto.class))
                .thenReturn(dto);
        when(identity.resolve(any(), eq(command)))
                .thenReturn(
                        new CdsIdentityDecisionDto(CdsIdentityDecisionDto.Outcome.NO_MATCH, null));
        EntityManager em = mock(EntityManager.class);
        AtomicInteger ids = new AtomicInteger(10);
        doAnswer(
                        call -> {
                            Object row = call.getArgument(0);
                            if (row instanceof Demographic patient) patient.setDemographicNo(42);
                            else if (row instanceof DemographicExt ext)
                                ext.setId(ids.incrementAndGet());
                            return null;
                        })
                .when(em)
                .persist(any());
        ReflectionTestUtils.setField(demographics, "entityManager", em);
        assertThat(demographics.write("run", 1, "unit", record, command, n -> {}))
                .hasSize(4)
                .containsEntry("demographic", "42");
        var patients = ArgumentCaptor.forClass(Demographic.class);
        verify(em).persist(patients.capture());
        assertThat(patients.getValue().getProviderNo()).isEqualTo("77");
        assertThat(patients.getValue().getPhone()).isEqualTo("613-555-0101");
        assertThat(patients.getValue().getMonthOfBirth()).isEqualTo("03");
        assertThat(patients.getValue().getDateOfBirth()).isEqualTo("04");
        assertThat(ReflectionTestUtils.getField(patients.getValue(), "prefName")).isEqualTo("");
        var extensions = ArgumentCaptor.forClass(DemographicExt.class);
        verify(em, times(3)).persist(extensions.capture());
        assertThat(extensions.getAllValues())
                .extracting(DemographicExt::getKey)
                .containsExactly("hPhoneExt", "wPhoneExt", "demo_cell");
        doAnswer(
                        call -> {
                            if (call.getArgument(0) instanceof Demographic patient)
                                patient.setDemographicNo(42);
                            return null;
                        })
                .when(em)
                .persist(any());
        assertThatThrownBy(() -> demographics.write("run", 1, "unit", record, command, n -> {}))
                .isInstanceOf(CdsImportException.class)
                .hasMessage("DEMOGRAPHIC_EXTENSION_NATIVE_ID_MISSING");
    }

    @Test
    void
            shouldFloorNativeObservationSecondsAndRetainSourcePrecision_whenFractionCouldRoundAcrossMidnight()
                    throws Exception {
        for (String value :
                new String[] {
                    "1999-12-31T23:59:59.999999999Z",
                    "1969-12-31T23:59:59.999999999Z",
                    "1969-12-31T18:59:59.999999999"
                }) {
            clearInvocations(preparation);
            var record =
                    section(
                            "ClinicalNotes",
                            "<MyClinicalNotesContent>Source note</MyClinicalNotesContent>"
                                    + "<EventDateTime><cdsd:FullDateTime>"
                                    + value
                                    + "</cdsd:FullDateTime></EventDateTime>");
            histories.prepare("run", 1, "unit", record, command);
            var dto = captured(CdsHistoryNativeDto.class);
            long expected =
                    value.startsWith("1999")
                            ? java.time.Instant.parse("1999-12-31T23:59:59Z").toEpochMilli()
                            : -1000L;
            assertThat(dto.observationMillis()).isEqualTo(expected);
            assertThat(dto.diagnostics())
                    .extracting(CdsClinicalDiagnosticDto::code)
                    .contains("CDS_NOTE_SUBSECOND_PRECISION_RETAINED_IN_SOURCE");
            assertThat(fields.firstToken(record, "EventDateTime/FullDateTime", 128))
                    .isEqualTo(value);
        }
    }

    @Test
    void shouldPreserveEverySourceCreationValueAndWarnOnce_whenParticipantsHaveDifferentPrecisions()
            throws Exception {
        var record =
                section(
                        "ClinicalNotes",
                        "<MyClinicalNotesContent>Source note</MyClinicalNotesContent>"
                            + "<ParticipatingProviders><Name><cdsd:FirstName>First</cdsd:FirstName><cdsd:LastName>Source</cdsd:LastName></Name>"
                            + "<DateTimeNoteCreated><cdsd:FullDateTime>1990-03-04T10:20:30.125-05:00</cdsd:FullDateTime></DateTimeNoteCreated></ParticipatingProviders>"
                            + "<ParticipatingProviders><Name><cdsd:FirstName>Second</cdsd:FirstName><cdsd:LastName>Source</cdsd:LastName></Name>"
                            + "<DateTimeNoteCreated><cdsd:YearOnly>1989</cdsd:YearOnly></DateTimeNoteCreated></ParticipatingProviders>");
        histories.prepare("run", 1, "unit", record, command);
        var dto = captured(CdsHistoryNativeDto.class);
        assertThat(dto.createdMillis()).isNull();
        assertThat(dto.diagnostics())
                .filteredOn(d -> d.code().equals("CDS_NOTE_CREATED_TIME_RETAINED_IN_SOURCE"))
                .hasSize(1);
        assertThat(
                        fields.firstToken(
                                record,
                                "ParticipatingProviders/DateTimeNoteCreated/FullDateTime",
                                128))
                .isEqualTo("1990-03-04T10:20:30.125-05:00");
        assertThat(
                        fields.firstToken(
                                record, "ParticipatingProviders/DateTimeNoteCreated/YearOnly", 128))
                .isEqualTo("1989");
        assertThat(dto.diagnostics())
                .extracting(CdsClinicalDiagnosticDto::code)
                .contains("CDS_MULTIPLE_NOTE_PARTICIPANTS_SOURCE_ONLY");
    }

    @Test
    void shouldWarnForExactSourceCreation_whenItsClockCannotBeTheNativeRevisionFormula()
            throws Exception {
        var record =
                section(
                        "ClinicalNotes",
                        "<MyClinicalNotesContent>Source note</MyClinicalNotesContent>"
                            + "<ParticipatingProviders><Name><cdsd:FirstName>First</cdsd:FirstName><cdsd:LastName>Source</cdsd:LastName></Name>"
                            + "<DateTimeNoteCreated><cdsd:FullDateTime>1990-03-04T10:20:30.125-05:00</cdsd:FullDateTime></DateTimeNoteCreated></ParticipatingProviders>");
        histories.prepare("run", 1, "unit", record, command);
        var dto = captured(CdsHistoryNativeDto.class);
        assertThat(dto.createdMillis()).isNotNull();
        assertThat(dto.diagnostics())
                .extracting(CdsClinicalDiagnosticDto::code)
                .contains("CDS_NOTE_CREATED_TIME_RETAINED_IN_SOURCE");
        assertThat(
                        fields.firstToken(
                                record,
                                "ParticipatingProviders/DateTimeNoteCreated/FullDateTime",
                                128))
                .isEqualTo("1990-03-04T10:20:30.125-05:00");
    }

    @Test
    void shouldWriteImportedNoteWithoutInventedSignatureOrCreatedTime_fromPreparedProjectionOnly()
            throws Exception {
        var dto =
                new CdsHistoryNativeDto(
                        "Complete source note", null, null, 636561630125L, "77", List.of());
        var record =
                section(
                        "ClinicalNotes",
                        "<MyClinicalNotesContent>Complete source note</MyClinicalNotesContent>");
        Files.delete(record.fieldsFile());
        Files.delete(record.valuesFile());
        when(preparation.load(
                        "run", 1, "unit", record.sha256(), command, CdsHistoryNativeDto.class))
                .thenReturn(dto);
        EntityManager em = mock(EntityManager.class);
        doAnswer(
                        call -> {
                            ((CaseManagementNote) call.getArgument(0)).setId(9L);
                            return null;
                        })
                .when(em)
                .persist(any(CaseManagementNote.class));
        ReflectionTestUtils.setField(histories, "entityManager", em);
        assertThat(histories.write("run", 1, "unit", 42, record, command, n -> {}))
                .containsEntry("casemgmt_note", "9");
        var note = ArgumentCaptor.forClass(CaseManagementNote.class);
        verify(em).persist(note.capture());
        assertThat(note.getValue().getProviderNo()).isEqualTo("77");
        assertThat(note.getValue().getSigning_provider_no()).isEmpty();
        assertThat(note.getValue().isSigned()).isFalse();
        assertThat(note.getValue().getCreate_date()).isNull();
        assertThat(note.getValue().getUpdate_date()).isCloseTo(new java.util.Date(), 5000);
        assertThat(note.getValue().getObservation_date()).isNull();
    }

    @Test
    void shouldPrepareDiscreteLanguageStatusExpiryAndFullPatientNote_withoutEncodingOrClipping()
            throws Exception {
        var source =
                parse(
                        "",
                        "<PreferredSpokenLanguage>eng</PreferredSpokenLanguage><NoteAboutPatient>Literal"
                            + " &lt;/unotes&gt; &amp; café 🩺</NoteAboutPatient>",
                        "<HealthCard><cdsd:Number>1234567890</cdsd:Number><cdsd:ProvinceCode>CA-ON</cdsd:ProvinceCode><cdsd:Expirydate>2028-01-31</cdsd:Expirydate></HealthCard>",
                        "<PersonStatusDate>2023-04-05</PersonStatusDate>");
        demographics.prepare("run", 1, "unit", source, command);
        var details = captured(CdsDemographicNativeDto.class).details();
        assertThat(details.spokenLanguage()).isEqualTo("English");
        assertThat(details.statusDate()).isEqualTo("2023-04-05");
        assertThat(details.healthCardExpiry()).isEqualTo("2028-01-31");
        assertThat(details.patientNote()).isEqualTo("Literal </unotes> & café 🩺");
        assertThat(details.diagnostics())
                .extracting(CdsClinicalDiagnosticDto::code)
                .contains("CDS_SPOKEN_LANGUAGE_NORMALIZED");
    }

    @Test
    void
            shouldRetainUnknownLanguageAndOversizedPatientNoteInCompleteSource_withExplicitNativeNotice()
                    throws Exception {
        String narrative = "🩺".repeat(16380);
        var source =
                parse(
                        "",
                        "<PreferredSpokenLanguage>ZZZ</PreferredSpokenLanguage>"
                                + "<NoteAboutPatient>"
                                + narrative
                                + "</NoteAboutPatient>");
        demographics.prepare("run", 1, "unit", source, command);
        var details = captured(CdsDemographicNativeDto.class).details();
        assertThat(details.spokenLanguage()).isNull();
        assertThat(details.patientNote()).contains("complete original").doesNotContain("🩺");
        assertThat(fields.firstText(source, "NoteAboutPatient", 40000)).isEqualTo(narrative);
        assertThat(details.diagnostics())
                .extracting(CdsClinicalDiagnosticDto::code)
                .contains(
                        "CDS_SPOKEN_LANGUAGE_SOURCE_ONLY",
                        "CDS_PATIENT_NOTE_COMPLETE_TEXT_SOURCE_VIEW");
    }

    @Test
    void shouldChooseUnambiguousLatestRosterTransition_notFirstOrArbitraryHistoricalChartSnapshot()
            throws Exception {
        var source =
                parse(
                        "",
                        "<Enrolment>"
                                + enrollment("1", "2000-01-01", null)
                                + enrollment("0", "2000-01-01", "2001-02-03")
                                + enrollment("1", "2005-06-07", null)
                                + "</Enrolment>");
        demographics.prepare("run", 1, "unit", source, command);
        var details = captured(CdsDemographicNativeDto.class).details();
        assertThat(details.enrollmentHistoryCount()).isEqualTo(3);
        assertThat(details.roster().status()).isEqualTo("RO");
        assertThat(details.roster().date()).isEqualTo("2005-06-07");
        assertThat(details.roster().terminationDate()).isNull();
        assertThat(details.diagnostics())
                .extracting(CdsClinicalDiagnosticDto::code)
                .contains("CDS_ENROLMENT_HISTORY_STRUCTURED_SOURCE_ONLY");
    }

    @Test
    void shouldMapAnEndedEnrolmentToNativeTerminatedState_withExactDates() throws Exception {
        demographics.prepare(
                "run",
                1,
                "unit",
                parse(
                        "",
                        "<Enrolment>"
                                + enrollment("0", "2000-01-01", "2001-02-03")
                                + "</Enrolment>"),
                command);
        var roster = captured(CdsDemographicNativeDto.class).details().roster();
        assertThat(roster.status()).isEqualTo("TE");
        assertThat(roster.date()).isEqualTo("2000-01-01");
        assertThat(roster.terminationDate()).isEqualTo("2001-02-03");
    }

    @Test
    void shouldRefuseToSelectAnAmbiguousLatestRoster_butAllowALaterUnambiguousTransition()
            throws Exception {
        String conflict =
                enrollment("1", "2000-01-01", null) + enrollment("0", "1999-01-01", "2000-01-01");
        demographics.prepare(
                "run", 1, "unit", parse("", "<Enrolment>" + conflict + "</Enrolment>"), command);
        var details = captured(CdsDemographicNativeDto.class).details();
        assertThat(details.roster()).isNull();
        assertThat(details.diagnostics())
                .extracting(CdsClinicalDiagnosticDto::code)
                .contains("CDS_ENROLMENT_CURRENT_REQUIRES_REVIEW");
        clearInvocations(preparation);
        demographics.prepare(
                "run",
                1,
                "unit",
                parse(
                        "",
                        "<Enrolment>"
                                + conflict
                                + enrollment("1", "2002-02-03", null)
                                + "</Enrolment>"),
                command);
        assertThat(captured(CdsDemographicNativeDto.class).details().roster().date())
                .isEqualTo("2002-02-03");
    }

    @Test
    void
            shouldRejectIncompleteOrConflictingRosterDatesDuringPreflight_insteadOfGuessingCurrentFacts()
                    throws Exception {
        for (String content :
                List.of(
                        "<Enrolment/>",
                        "<Enrolment>" + enrollment("0", "2000-01-01", null) + "</Enrolment>",
                        "<Enrolment>"
                                + enrollment("1", "2000-01-01", "2001-01-01")
                                + "</Enrolment>")) {
            var source = parse("", content);
            assertThatThrownBy(() -> demographics.prepare("run", 1, "unit", source, command))
                    .isInstanceOf(
                            io.github.carlos_emr.carlos.demographic.cdsimport.support
                                    .CdsClinicalException.class);
        }
        verifyNoInteractions(preparation);
    }

    @Test
    void shouldPreserveLiteralPatientNoteFramingAndLegacyRawText() {
        String text = "Literal </unotes> & <tag> café 🩺";
        assertThat(
                        io.github.carlos_emr.carlos.commn.model.DemographicCust.patientNotesText(
                                io.github.carlos_emr.carlos.commn.model.DemographicCust
                                        .wrapPatientNotes(text)))
                .isEqualTo(text);
        assertThat(
                        io.github.carlos_emr.carlos.commn.model.DemographicCust.patientNotesText(
                                "legacy raw <text>"))
                .isEqualTo("legacy raw <text>");
        assertThat(io.github.carlos_emr.carlos.commn.model.DemographicCust.patientNotesText(null))
                .isEmpty();
    }

    private static String enrollment(String status, String start, String end) {
        return "<EnrolmentHistory><EnrollmentStatus>"
                + status
                + "</EnrollmentStatus>"
                + (start == null ? "" : "<EnrollmentDate>" + start + "</EnrollmentDate>")
                + (end == null
                        ? ""
                        : "<EnrollmentTerminationDate>" + end + "</EnrollmentTerminationDate>")
                + "</EnrolmentHistory>";
    }

    private CdsRecordDto corrected(CdsRecordDto raw, CdsFieldDto field, String replacement)
            throws Exception {
        Path value = raw.fieldsFile().getParent().resolve("replacement.utf8");
        Files.writeString(value, replacement);
        String replacementDigest =
                io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsPaths.hex(
                        java.security.MessageDigest.getInstance("SHA-256")
                                .digest(Files.readAllBytes(value)));
        var entry =
                java.util.Map.<String, Object>of(
                        "sourceXPath",
                        raw.sourceXPath() + "/" + field.path(),
                        "namespace",
                        field.namespace(),
                        "kind",
                        field.kind(),
                        "schemaType",
                        field.schemaType(),
                        "whitespace",
                        field.whitespace(),
                        "originalSha256",
                        field.sha256(),
                        "originalLength",
                        field.length(),
                        "replacementFile",
                        "replacement.utf8",
                        "replacementSha256",
                        replacementDigest,
                        "replacementBytes",
                        Files.size(value));
        byte[] document =
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .writeValueAsBytes(
                                java.util.Map.of(
                                        "version",
                                        1,
                                        "acceptedXmlSha256",
                                        "d".repeat(64),
                                        "corrections",
                                        List.of(entry)));
        Path manifest = raw.fieldsFile().getParent().resolve("review.json");
        Files.write(manifest, document);
        return raw.withCorrections(
                new CdsCorrectionSetDto(
                        manifest,
                        io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsPaths.hex(
                                java.security.MessageDigest.getInstance("SHA-256")
                                        .digest(document)),
                        "d".repeat(64)));
    }

    private <T> T captured(Class<T> type) throws Exception {
        var capture = ArgumentCaptor.forClass(Object.class);
        verify(preparation)
                .store(eq("run"), eq(1L), eq("unit"), anyString(), eq(command), capture.capture());
        return type.cast(capture.getValue());
    }

    private CdsRecordDto appointment(String time, String duration, String date, String notes)
            throws Exception {
        return appointment(time, duration, date, "", notes);
    }

    private CdsRecordDto appointment(
            String time, String duration, String date, String status, String notes)
            throws Exception {
        return section(
                "Appointments",
                "<AppointmentTime>"
                        + time
                        + "</AppointmentTime>"
                        + (duration == null ? "" : "<Duration>" + duration + "</Duration>")
                        + status
                        + "<AppointmentDate>"
                        + date
                        + "</AppointmentDate>"
                        + notes);
    }

    private CdsRecordDto section(String section, String contents) throws Exception {
        return parse("<" + section + ">" + contents + "</" + section + ">", "");
    }

    private CdsRecordDto parse(String extra, String demographicExtra) throws Exception {
        return parse(extra, demographicExtra, "", "");
    }

    private CdsRecordDto parse(
            String extra, String demographicExtra, String healthCard, String statusDate)
            throws Exception {
        int number = sequence.incrementAndGet();
        Path source = temporary.resolve("source-" + number + ".xml");
        String demographic =
                "<Demographics><Names><cdsd:LegalName"
                    + " namePurpose='L'><cdsd:FirstName><cdsd:Part>Import</cdsd:Part><cdsd:PartType>GIV</cdsd:PartType></cdsd:FirstName><cdsd:LastName><cdsd:Part>Fixture</cdsd:Part><cdsd:PartType>FAMC</cdsd:PartType></cdsd:LastName></cdsd:LegalName></Names><DateOfBirth>1971-03-04</DateOfBirth>"
                        + healthCard
                        + "<Gender>F</Gender><UniqueVendorIdSequence>owned</UniqueVendorIdSequence>"
                        + demographicExtra
                        + "<PersonStatusCode><PersonStatusAsEnum>A</PersonStatusAsEnum></PersonStatusCode>"
                        + statusDate
                        + "</Demographics>";
        Files.writeString(
                source,
                "<OmdCds xmlns='cds' xmlns:cdsd='cds_dt'><PatientRecord>"
                        + demographic
                        + extra
                        + "</PatientRecord></OmdCds>");
        List<CdsRecordDto> records = new ArrayList<>();
        new CdsXmlParser()
                .parse(
                        source,
                        temporary.resolve("spool-" + number),
                        CdsInputLimitsDto.defaults(),
                        () -> false,
                        records::add);
        return records.get(records.size() - 1);
    }

    private static String address(String kind, String street, String city) {
        return "<Address addressType='"
                + kind
                + "'><cdsd:Structured><cdsd:Line1>"
                + street
                + "</cdsd:Line1><cdsd:City>"
                + city
                + "</cdsd:City><cdsd:CountrySubdivisionCode>CA-ON</cdsd:CountrySubdivisionCode></cdsd:Structured></Address>";
    }
}
