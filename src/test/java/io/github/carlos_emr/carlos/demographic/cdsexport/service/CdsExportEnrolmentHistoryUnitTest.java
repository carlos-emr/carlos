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
import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport;
import io.github.carlos_emr.carlos.commn.dto.CdsExportDemographicRowDto;
import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportCommand;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.validator.CdsExportXmlValidator;
import java.io.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

@Tag("unit")
@Tag("cds-export")
class CdsExportEnrolmentHistoryUnitTest {
    static final String HASH = "a".repeat(64);
    @TempDir Path directory;

    @Test
    void mapsExactCurrentAndTerminatedEventsWithTheActualEnrolledProvider() throws Exception {
        var mapper = new CdsExportEnrolmentMapper();
        var current =
                mapper.map(27, current("RO", "2022-03-01", null, null, "0é27"), provider("0é27"));
        assertThat(current.representable()).isTrue();
        assertThat(current.event().status()).isEqualTo("1");
        assertThat(current.event().physician().firstName()).isEqualTo("Élodie😀");
        assertThat(current.fields()).hasSize(11);
        assertThat(current.event().physician().cpsoNumber()).isNull();
        var old =
                mapper.map(
                        27,
                        archive(1, "TE", "2022-03-01", "2023-03-02", "12", "0é27"),
                        provider("0é27"));
        assertThat(old.representable()).isTrue();
        assertThat(old.event().status()).isEqualTo("0");
        assertThat(old.event().end()).isEqualTo(LocalDate.parse("2023-03-02"));
    }

    @Test
    void neverInfersTerminatedHistoryFromANotRosteredOrMissingStatus() throws Exception {
        for (String status : Arrays.asList(null, "", "NR")) {
            var result =
                    new CdsExportEnrolmentMapper()
                            .map(27, current(status, null, null, null, null), null);
            assertThat(result.representable()).isTrue();
            assertThat(result.event()).isNull();
            assertThat(result.fields().get(key("roster_status")).presence().name())
                    .isEqualTo(status == null ? "SQL_NULL" : status.isEmpty() ? "EMPTY" : "VALUE");
        }
        assertThat(
                        new CdsExportEnrolmentMapper()
                                .map(27, current("NR", "2020-01-01", null, null, null), null)
                                .representable())
                .isFalse();
    }

    @Test
    void datesComeOnlyFromExplicitRosterDates() throws Exception {
        for (String start :
                Arrays.asList(
                        null,
                        "",
                        "0000-00-00",
                        "2024-02-30",
                        "2024-2-03",
                        "2024-02",
                        " 2024-02-03")) {
            var row = current("RO", start, null, null, null);
            var values = new TreeMap<>(row.fields());
            values.put("date_joined", "2024-02-03");
            values.put("lastUpdateDate", "2024-02-03 13:00:00");
            assertThat(
                            new CdsExportEnrolmentMapper()
                                    .map(
                                            27,
                                            new CdsExportCapturedTextRowDto(
                                                    row.table(), row.recordKey(), values),
                                            null)
                                    .representable())
                    .isFalse();
        }
    }

    @Test
    void refusesUnknownAndWhitespaceNormalizedRosterStates() throws Exception {
        for (String status : List.of("0", "1", "ro", "RO ", "TE\n", "UNKNOWN", "NU"))
            assertThat(
                            new CdsExportEnrolmentMapper()
                                    .map(27, current(status, "2020-01-01", null, null, null), null)
                                    .representable())
                    .isFalse();
    }

    @Test
    void enforcesTheGuideConditionalDatesAndConsistentState() throws Exception {
        for (var row :
                List.of(
                        current("TE", "2020-01-01", null, null, null),
                        current("TE", "2022-01-01", "2021-12-31", null, null),
                        current("RO", "2020-01-01", "2021-01-01", null, null),
                        current("RO", "2020-01-01", null, "12", null),
                        current("TE", "2020-01-01", "0000-00-00", null, null)))
            assertThat(new CdsExportEnrolmentMapper().map(27, row, null).representable()).isFalse();
        assertThat(
                        new CdsExportEnrolmentMapper()
                                .map(
                                        27,
                                        current("TE", "2020-01-01", "2020-01-01", null, null),
                                        null)
                                .representable())
                .isTrue();
    }

    @Test
    void usesTheExactPinnedTerminationCodesAndDoesNotTrimThem() throws Exception {
        for (String reason : CdsExportEnrolmentEventDto.TERMINATION_REASONS)
            assertThat(
                            new CdsExportEnrolmentMapper()
                                    .map(
                                            27,
                                            current("TE", "2020-01-01", "2020-01-02", reason, null),
                                            null)
                                    .representable())
                    .isTrue();
        for (String reason : List.of("99", "012", " 12", "12 ", "A", "é"))
            assertThat(
                            new CdsExportEnrolmentMapper()
                                    .map(
                                            27,
                                            current("TE", "2020-01-01", "2020-01-02", reason, null),
                                            null)
                                    .representable())
                    .isFalse();
        var factory = DocumentBuilderFactory.newDefaultNSInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        try (var input =
                getClass()
                        .getResourceAsStream(
                                "/cds-import/schema/EMR_Data_Migration_Schema_DT.xsd")) {
            var document = factory.newDocumentBuilder().parse(input);
            var types =
                    document.getElementsByTagNameNS(
                            XMLConstants.W3C_XML_SCHEMA_NS_URI, "simpleType");
            var codes = new HashSet<String>();
            for (int i = 0; i < types.getLength(); i++) {
                var node = (org.w3c.dom.Element) types.item(i);
                if (!node.getAttribute("name").equals("terminationReasonCode")) continue;
                var values =
                        node.getElementsByTagNameNS(
                                XMLConstants.W3C_XML_SCHEMA_NS_URI, "enumeration");
                for (int j = 0; j < values.getLength(); j++)
                    codes.add(((org.w3c.dom.Element) values.item(j)).getAttribute("value"));
            }
            assertThat(codes).isEqualTo(CdsExportEnrolmentEventDto.TERMINATION_REASONS);
        }
    }

    @Test
    void missingOrDifferentExactProviderReferencesNeverFallBackToThePrimaryPhysician()
            throws Exception {
        var row = current("RO", "2020-01-01", null, null, "0é27");
        assertThat(new CdsExportEnrolmentMapper().map(27, row, null).representable()).isFalse();
        assertThat(new CdsExportEnrolmentMapper().map(27, row, provider("0e27")).representable())
                .isFalse();
        assertThatThrownBy(
                        () ->
                                new CdsExportEnrolmentMapper()
                                        .map(
                                                27,
                                                current(null, null, null, null, null),
                                                provider("0é27")))
                .hasMessage("CDS_EXPORT_ENROLMENT_SOURCE_SCOPE_INVALID");
    }

    @Test
    void unknownOhipAndOverlengthProviderDataCannotCreateAnOtherwiseValidEvent() throws Exception {
        var fields = new TreeMap<>(provider("0é27").fields());
        fields.put("ohip_no", "1234567");
        assertThat(
                        new CdsExportEnrolmentMapper()
                                .map(
                                        27,
                                        current("RO", "2020-01-01", null, null, "0é27"),
                                        new CdsExportCapturedTextRowDto(
                                                "provider", provider("0é27").recordKey(), fields))
                                .representable())
                .isFalse();
    }

    @Test
    void fullRowIdentityColumnSetsAndCanonicalKeysAreMandatory() throws Exception {
        var original = current("RO", "2020-01-01", null, null, null);
        assertThatThrownBy(() -> new CdsExportEnrolmentMapper().map(28, original, null))
                .hasMessage("CDS_EXPORT_ENROLMENT_SOURCE_SCOPE_INVALID");
        assertThatThrownBy(
                        () ->
                                new CdsExportEnrolmentMapper()
                                        .map(
                                                27,
                                                new CdsExportCapturedTextRowDto(
                                                        "demographic", "27", original.fields()),
                                                null))
                .hasMessage("CDS_EXPORT_ENROLMENT_SOURCE_SCOPE_INVALID");
        var incomplete = new TreeMap<>(original.fields());
        incomplete.remove("citizenship");
        assertThatThrownBy(
                        () ->
                                new CdsExportEnrolmentMapper()
                                        .map(
                                                27,
                                                new CdsExportCapturedTextRowDto(
                                                        "demographic",
                                                        original.recordKey(),
                                                        incomplete),
                                                null))
                .hasMessage("CDS_EXPORT_ENROLMENT_SOURCE_SCOPE_INVALID");
        var archived = archive(3, "RO", "2020-01-01", null, null, null);
        var values = new TreeMap<>(archived.fields());
        values.put("id", "03");
        var altered =
                new CdsExportCapturedTextRowDto("demographicArchive", archived.recordKey(), values);
        assertThatThrownBy(() -> new CdsExportEnrolmentMapper().map(27, altered, null))
                .hasMessage("CDS_EXPORT_ENROLMENT_SOURCE_SCOPE_INVALID");
    }

    @Test
    void identicalSnapshotVersionsHaveOneEventAndKeepEverySourceFieldDisposition()
            throws Exception {
        var history = history();
        history.accept(archive(1, "RO", "2020-01-01", null, null, null), null);
        history.accept(archive(2, "RO", "2020-01-01", null, null, null), null);
        history.accept(current("RO", "2020-01-01", null, null, null), null);
        var result = history.finish(2);
        assertThat(result.events()).hasSize(1);
        assertThat(result.sourceRows()).isEqualTo(3);
        assertThat(result.fields()).hasSize(15);
    }

    @Test
    void distinctCompletedEpisodesAndPhysiciansAreNotCollapsed() throws Exception {
        var history = history();
        history.accept(archive(1, "RO", "2020-01-01", null, null, "A"), provider("A"));
        history.accept(archive(2, "TE", "2020-01-01", "2021-01-01", "12", "A"), provider("A"));
        history.accept(archive(3, "RO", "2022-01-01", null, null, "A"), provider("A"));
        history.accept(archive(4, "TE", "2022-01-01", "2023-01-01", "14", "A"), provider("A"));
        history.accept(current("RO", "2024-01-01", null, null, "B"), provider("B"));
        assertThat(history.finish(4).events()).hasSize(5);
    }

    @Test
    void conflictingVersionsOfOneTerminationAreReviewCasesRatherThanExtraClinicalEvents()
            throws Exception {
        for (String changed : List.of("reason", "end")) {
            var history = history();
            history.accept(archive(1, "TE", "2020-01-01", "2021-01-01", "12", "A"), provider("A"));
            var next =
                    archive(
                            2,
                            "TE",
                            "2020-01-01",
                            changed.equals("end") ? "2021-02-01" : "2021-01-01",
                            changed.equals("reason") ? "14" : "12",
                            "A");
            assertThatThrownBy(() -> history.accept(next, provider("A")))
                    .hasMessage("CDS_EXPORT_ENROLMENT_HISTORY_AMBIGUOUS_VERSION");
            assertThatThrownBy(() -> history.finish(1))
                    .hasMessage("CDS_EXPORT_ENROLMENT_HISTORY_CLOSED");
        }
    }

    @Test
    void aReopenedOldEpisodeRequiresAnExplicitCorrectionDecision() throws Exception {
        var history = history();
        history.accept(archive(1, "TE", "2020-01-01", "2021-01-01", "12", null), null);
        assertThatThrownBy(
                        () -> history.accept(current("RO", "2020-01-01", null, null, null), null))
                .hasMessage("CDS_EXPORT_ENROLMENT_HISTORY_AMBIGUOUS_VERSION");
    }

    @Test
    void aClearedCurrentRosterCannotSilentlyReplayAnOldActiveState() throws Exception {
        for (String state : Arrays.asList(null, "", "NR")) {
            var history = history();
            history.accept(archive(1, "RO", "2020-01-01", null, null, null), null);
            history.accept(current(state, null, null, null, null), null);
            assertThatThrownBy(() -> history.finish(1))
                    .hasMessage("CDS_EXPORT_ENROLMENT_HISTORY_AMBIGUOUS_VERSION");
        }
        var ended = history();
        ended.accept(archive(1, "RO", "2020-01-01", null, null, null), null);
        ended.accept(archive(2, "TE", "2020-01-01", "2021-01-01", null, null), null);
        ended.accept(current("NR", null, null, null, null), null);
        assertThat(ended.finish(2).events()).hasSize(2);
    }

    @Test
    void anOlderCurrentEffectiveDateCannotBeOverriddenByANewerArchivedEvent() throws Exception {
        var history = history();
        history.accept(archive(1, "TE", "2020-01-01", "2025-01-01", null, "A"), provider("A"));
        history.accept(current("RO", "2024-01-01", null, null, "B"), provider("B"));
        assertThatThrownBy(() -> history.finish(1))
                .hasMessage("CDS_EXPORT_ENROLMENT_HISTORY_AMBIGUOUS_VERSION");
    }

    @Test
    void anUnclosedEpisodeCannotBeSilentlyTurnedIntoMultipleEnrolmentsByAnEditedDateOrPhysician()
            throws Exception {
        for (String change : List.of("date", "physician")) {
            var history = history();
            history.accept(archive(1, "RO", "2020-01-01", null, null, "A"), provider("A"));
            var row =
                    current(
                            "RO",
                            change.equals("date") ? "2022-01-01" : "2020-01-01",
                            null,
                            null,
                            change.equals("physician") ? "B" : "A");
            assertThatThrownBy(
                            () ->
                                    history.accept(
                                            row, provider(row.fields().get("roster_enrolled_to"))))
                    .hasMessage("CDS_EXPORT_ENROLMENT_HISTORY_AMBIGUOUS_VERSION");
            assertThatThrownBy(() -> history.finish(1))
                    .hasMessage("CDS_EXPORT_ENROLMENT_HISTORY_CLOSED");
        }
    }

    @Test
    void aCaughtBadRowPermanentlyInvalidatesTheHistory() throws Exception {
        var history = history();
        assertThatThrownBy(() -> history.accept(archive(1, "RO", null, null, null, null), null))
                .hasMessage("CDS_EXPORT_ENROLMENT_HISTORY_REQUIRES_REVIEW");
        assertThatThrownBy(() -> history.accept(current(null, null, null, null, null), null))
                .hasMessage("CDS_EXPORT_ENROLMENT_HISTORY_CLOSED");
        assertThatThrownBy(() -> history.finish(0))
                .hasMessage("CDS_EXPORT_ENROLMENT_HISTORY_CLOSED");
    }

    @Test
    void allArchiveRowsAndExactlyOneCurrentRowMustBeConsumed() throws Exception {
        var history = history();
        history.accept(archive(1, null, null, null, null, null), null);
        assertThatThrownBy(() -> history.finish(1))
                .hasMessage("CDS_EXPORT_ENROLMENT_HISTORY_SCOPE_INVALID");
        var partial = history();
        partial.accept(current(null, null, null, null, null), null);
        assertThatThrownBy(() -> partial.finish(1))
                .hasMessage("CDS_EXPORT_ENROLMENT_HISTORY_SCOPE_INVALID");
    }

    @Test
    void lateArchivesReversedKeysAndRepeatedRowsCannotPassTheHistoryFence() throws Exception {
        var late = history();
        late.accept(current(null, null, null, null, null), null);
        assertThatThrownBy(() -> late.accept(archive(1, null, null, null, null, null), null))
                .hasMessage("CDS_EXPORT_ENROLMENT_HISTORY_SCOPE_INVALID");
        var reversed = history();
        reversed.accept(archive(2, null, null, null, null, null), null);
        assertThatThrownBy(() -> reversed.accept(archive(1, null, null, null, null, null), null))
                .hasMessage("CDS_EXPORT_ENROLMENT_HISTORY_SCOPE_INVALID");
        var duplicate = history();
        duplicate.accept(archive(1, null, null, null, null, null), null);
        assertThatThrownBy(() -> duplicate.accept(archive(1, null, null, null, null, null), null))
                .hasMessage("CDS_EXPORT_ENROLMENT_HISTORY_SCOPE_INVALID");
    }

    @Test
    void countByteAndCancellationLimitsLeaveNoUsableHistory() throws Exception {
        var limited = new CdsExportEnrolmentHistory(27, 1, 1L << 20, () -> {});
        limited.accept(archive(1, null, null, null, null, null), null);
        assertThatThrownBy(() -> limited.accept(current(null, null, null, null, null), null))
                .hasMessage("CDS_EXPORT_ENROLMENT_HISTORY_SCOPE_INVALID");
        var bytes = new CdsExportEnrolmentHistory(27, 3, 64, () -> {});
        assertThatThrownBy(() -> bytes.accept(current(null, null, null, null, null), null))
                .hasMessage("CDS_EXPORT_ENROLMENT_HISTORY_RESOURCE_LIMIT");
        var cancelled =
                new CdsExportEnrolmentHistory(
                        27,
                        3,
                        1L << 20,
                        () -> {
                            throw new IllegalStateException("CANCELLED");
                        });
        assertThatThrownBy(() -> cancelled.accept(current(null, null, null, null, null), null))
                .hasMessage("CANCELLED");
        assertThatThrownBy(() -> cancelled.finish(0))
                .hasMessage("CDS_EXPORT_ENROLMENT_HISTORY_CLOSED");
    }

    @Test
    void actualSchemaAndReceivingImporterRetainTheCompleteNativeEventSequenceAndPatientNote()
            throws Exception {
        var history = history();
        history.accept(archive(1, "RO", "2020-01-01", null, null, "A"), provider("A"));
        history.accept(archive(2, "TE", "2020-01-01", "2021-01-01", "12", "A"), provider("A"));
        var row = current("RO", "2022-01-01", null, null, "B");
        history.accept(row, provider("B"));
        var result = history.finish(2);
        var note =
                new CdsExportPatientNoteMapper()
                        .map(
                                27,
                                Set.of(CdsCategory.DEMOGRAPHICS),
                                CdsExportPatientNoteMapperUnitTest.row(
                                        "  Native café 😀\r\nnote  "));
        var projection = projection(row, result, note);
        var records = parse(projection);
        var parser = new CdsFieldParser();
        var statuses = new ArrayList<String>();
        parser.forEachField(
                records.getFirst(),
                field -> {
                    if (field.path()
                            .matches(
                                    "Enrolment\\[1\\]/EnrolmentHistory\\[[1-9][0-9]*\\]/EnrollmentStatus\\[1\\]"))
                        try {
                            statuses.add(parser.readLogicalText(records.getFirst(), field, 1));
                        } catch (IOException error) {
                            throw new UncheckedIOException(error);
                        }
                });
        assertThat(statuses).containsExactly("1", "0", "1");
        assertThat(parser.firstText(records.getFirst(), "NoteAboutPatient", 64000))
                .isEqualTo(note.text());
        var command =
                new CdsImportCommand(
                        directory.resolve("input.xml"),
                        "enrolment-fixture",
                        "27",
                        null,
                        null,
                        "synthetic",
                        "omd-5.2",
                        false,
                        false,
                        false,
                        0,
                        null,
                        false);
        new CdsDemographicDetailsService(parser, null).validate(records.getFirst(), command);
    }

    @Test
    void absentOrNeverEnrolledRowsDoNotEmitAnEmptyEnrolmentElement() throws Exception {
        var row = current("NR", null, null, null, null);
        var history = history();
        history.accept(row, null);
        var projection = projection(row, history.finish(0), null);
        var records = parse(projection);
        var parser = new CdsFieldParser();
        assertThat(
                        parser.firstText(
                                records.getFirst(),
                                "Enrolment/EnrolmentHistory/EnrollmentStatus",
                                1))
                .isNull();
    }

    @Test
    void projectionDispositionsCannotBeDetachedFromTheirPatientAndCurrentRow() throws Exception {
        var row = current(null, null, null, null, null);
        var history = history();
        history.accept(row, null);
        var result = history.finish(0);
        var good = projection(row, result, null);
        var fields = new HashMap<>(good.enrolmentFields());
        fields.remove(key("roster_status"));
        assertThatThrownBy(
                        () ->
                                new CdsExportDemographicEnrolmentProjectionDto(
                                        HASH, good.demographic(), 1, 0, null, List.of(), fields))
                .hasMessage("CDS_EXPORT_DEMOGRAPHIC_ENROLMENT_PROJECTION_INVALID");
        assertThatThrownBy(() -> good.events().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(good.toString()).doesNotContain("Élodie");
    }

    @Test
    void mappedEnrolmentStatesCannotDisappearBehindAnEmptyOutputList() throws Exception {
        var row = current("RO", "2020-01-01", null, null, null);
        var history = history();
        history.accept(row, null);
        var projected = projection(row, history.finish(0), null);
        assertThatThrownBy(
                        () ->
                                new CdsExportDemographicEnrolmentProjectionDto(
                                        HASH,
                                        projected.demographic(),
                                        1,
                                        0,
                                        null,
                                        List.of(),
                                        projected.enrolmentFields()))
                .hasMessage("CDS_EXPORT_DEMOGRAPHIC_ENROLMENT_PROJECTION_INVALID");
    }

    @Test
    void equalFieldCountsCannotHideAReplacedHistoricalRowIdentity() throws Exception {
        var history = history();
        history.accept(archive(1, "RO", "2020-01-01", null, null, null), null);
        history.accept(archive(2, "TE", "2020-01-01", "2021-01-01", null, null), null);
        var row = current("RO", "2022-01-01", null, null, null);
        history.accept(row, null);
        var projected = projection(row, history.finish(2), null);
        var fields = new HashMap<>(projected.enrolmentFields());
        var original =
                new CdsExportNativeFieldKeyDto(
                        "demographicArchive",
                        CdsExportPatientTableSourceReader.key(1),
                        "roster_date");
        var value = fields.remove(original);
        fields.put(
                new CdsExportNativeFieldKeyDto(
                        "demographicArchive",
                        CdsExportPatientTableSourceReader.key(9),
                        "roster_date"),
                value);
        assertThat(fields).hasSize(projected.enrolmentFields().size());
        assertThatThrownBy(
                        () ->
                                new CdsExportDemographicEnrolmentProjectionDto(
                                        HASH,
                                        projected.demographic(),
                                        3,
                                        0,
                                        null,
                                        projected.events(),
                                        fields))
                .hasMessage("CDS_EXPORT_DEMOGRAPHIC_ENROLMENT_PROJECTION_INVALID");
    }

    static CdsExportEnrolmentHistory history() {
        return new CdsExportEnrolmentHistory(27, 64, 1L << 20, () -> {});
    }

    static CdsExportCapturedTextRowDto current(
            String status, String start, String end, String reason, String provider) {
        var values = new TreeMap<String, String>();
        CdsExportDemographicRowDto.COLUMNS.forEach(column -> values.put(column, null));
        values.putAll(
                Map.of(
                        "demographic_no",
                        "27",
                        "first_name",
                        "Synthetic",
                        "last_name",
                        "Fixture",
                        "year_of_birth",
                        "1980",
                        "month_of_birth",
                        "01",
                        "date_of_birth",
                        "01",
                        "sex",
                        "U",
                        "patient_status",
                        "AC"));
        roster(values, status, start, end, reason, provider);
        return new CdsExportCapturedTextRowDto("demographic", "0000000027", values);
    }

    static CdsExportCapturedTextRowDto archive(
            long id, String status, String start, String end, String reason, String provider) {
        var values = new TreeMap<String, String>();
        CdsExportPatientTableReadSupport.Plan.DEMOGRAPHIC_ARCHIVE
                .columns()
                .forEach(column -> values.put(column, null));
        values.put("demographic_no", "27");
        values.put("id", Long.toString(id));
        roster(values, status, start, end, reason, provider);
        return new CdsExportCapturedTextRowDto(
                "demographicArchive", CdsExportPatientTableSourceReader.key(id), values);
    }

    private static void roster(
            Map<String, String> values,
            String status,
            String start,
            String end,
            String reason,
            String provider) {
        values.put("roster_status", status);
        values.put("roster_date", start);
        values.put("roster_termination_date", end);
        values.put("roster_termination_reason", reason);
        values.put("roster_enrolled_to", provider);
    }

    static CdsExportCapturedTextRowDto provider(String id) {
        return new CdsExportCapturedTextRowDto(
                "provider",
                CdsExportPublicProviderSourceReader.key(id),
                Map.of(
                        "provider_no",
                        id,
                        "first_name",
                        "Élodie😀",
                        "last_name",
                        "Fixture",
                        "ohip_no",
                        "123456",
                        "practitionerNoType",
                        "CPSO",
                        "practitionerNo",
                        "654321"));
    }

    static CdsExportNativeFieldKeyDto key(String column) {
        return new CdsExportNativeFieldKeyDto("demographic", "0000000027", column);
    }

    static CdsExportDemographicEnrolmentProjectionDto projection(
            CdsExportCapturedTextRowDto row,
            CdsExportEnrolmentHistory.Result history,
            CdsExportPatientNoteMappingDto note) {
        var values = new TreeMap<>(row.fields());
        values.remove("demographic_no");
        var base = new CdsExportDemographicMapper().map(new CdsExportDemographicRowDto(27, values));
        var fields = new HashMap<>(base.fields());
        CdsExportEnrolmentMapper.COLUMNS
                .keySet()
                .forEach(
                        column -> {
                            var item = history.fields().get(key(column));
                            fields.put(
                                    column,
                                    new CdsExportDemographicMappingDto.Assessment(
                                            CdsExportDemographicMappingDto.Presence.valueOf(
                                                    item.presence().name()),
                                            CdsExportDemographicMappingDto.Outcome.valueOf(
                                                    item.outcome().name()),
                                            item.destination(),
                                            item.rule()));
                        });
        var mapped =
                new CdsExportDemographicMappingDto(
                        base.patient(),
                        base.names(),
                        base.birthDate(),
                        base.healthCard(),
                        base.chartNumber(),
                        base.administrativeGender(),
                        base.addresses(),
                        base.phones(),
                        base.officialLanguage(),
                        base.spokenLanguage(),
                        base.email(),
                        base.personStatus(),
                        base.enumeratedStatus(),
                        base.personStatusDate(),
                        base.sin(),
                        fields,
                        base.primaryPhysician(),
                        base.relatedFields());
        return new CdsExportDemographicEnrolmentProjectionDto(
                HASH,
                mapped,
                history.sourceRows(),
                note == null ? 0 : 1,
                note,
                history.events(),
                history.fields());
    }

    private List<CdsRecordDto> parse(CdsExportDemographicEnrolmentProjectionDto value)
            throws Exception {
        var selected = Set.of(CdsCategory.DEMOGRAPHICS);
        var section =
                new CdsExportXmlComposer.Section(
                        new CdsExportIdentityDto(HASH, CdsCategory.DEMOGRAPHICS, "b".repeat(64)),
                        xml -> new CdsExportDemographicXmlWriter().writeWithEnrolment(value, xml));
        var output = new ByteArrayOutputStream();
        new CdsExportXmlComposer()
                .compose(
                        output,
                        HASH,
                        HASH,
                        "2026.08.0-test",
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        selected,
                        List.of(section).iterator(),
                        ignored -> {},
                        1L << 20,
                        () -> {});
        new CdsExportXmlValidator()
                .validate(
                        new ByteArrayInputStream(output.toByteArray()),
                        "27",
                        selected,
                        "2026.08.0-test",
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        CdsExportXmlLimitsDto.defaults(),
                        ignored -> {},
                        () -> {});
        var input =
                Files.write(directory.resolve(UUID.randomUUID() + ".xml"), output.toByteArray());
        var records = new ArrayList<CdsRecordDto>();
        new CdsXmlParser()
                .parse(
                        input,
                        directory.resolve(UUID.randomUUID().toString()),
                        CdsInputLimitsDto.defaults(),
                        () -> false,
                        records::add);
        return records;
    }
}
