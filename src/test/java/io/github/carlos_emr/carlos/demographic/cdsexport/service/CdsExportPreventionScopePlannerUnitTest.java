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
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportCapturedTextRowDto;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Independent complete partitions, clinical category ambiguity, restriction and raw-source boundaries. */
class CdsExportPreventionScopePlannerUnitTest {
    private static CdsExportPreventionClassifier classifier() throws IOException {
        return CdsExportPreventionClassifier.fromCaptured(
                "a".repeat(64),
                "<preventions><item name='Td' layout='injection' healthCanadaType='Td'/><item name='H1N1' layout='h1n1' healthCanadaType='Inf'/><item name='PAP' layout='PAPMAM' headingName='Screenings'/><item name='Smoking' layout='history' headingName='Screenings'/><item name='future' layout='unknown'/></preventions>"
                        .getBytes(StandardCharsets.UTF_8),
                List.of());
    }

    private static CdsExportPreventionScopePlanner.Result plan(
            List<CdsExportCapturedTextRowDto> rows) throws IOException {
        return new CdsExportPreventionScopePlanner().plan(27, classifier(), rows);
    }

    // otherPreventions is the PastHealth screening selection, captured and exported as PastHealth
    // procedures; it is not an excluded remainder (review findings F1/P1).
    @Test
    void shouldVerifyPartitionsEveryNativeMetadataOccurrenceIncludingH1n1AndUnknownTypes_forPreventionScopePlannerContract() throws Exception {
        var result =
                plan(
                        List.of(
                                row(-1, "Td", "0", null),
                                row(0, "PAP", "0", null),
                                row(1, "H1N1", null, null),
                                row(2, "Smoking", "0", null),
                                row(3, "unknown", "0", null)));
        assertThat(result.immunizations()).containsExactlyInAnyOrder(-1, 1);
        assertThat(result.otherPreventions()).containsExactlyInAnyOrder(0, 2);
        assertThat(result.reviews())
                .containsExactlyEntriesOf(
                        Map.of(3, CdsExportPreventionScopePlanner.Review.UNKNOWN_TYPE));
        assertThat(result.rows()).isEqualTo(5);
        assertThat(result.representable()).isFalse();
    }

    @Test
    void shouldSelectEveryBundledScreeningType_forPastHealthCapture() throws Exception {
        byte[] xml;
        try (var input = getClass().getResourceAsStream("/oscar/prevention/PreventionItems.xml")) {
            assertThat(input).isNotNull();
            xml = input.readAllBytes();
        }
        var bundled = CdsExportPreventionClassifier.fromCaptured("a".repeat(64), xml, List.of());
        var names =
                List.of(
                        "Tuberculosis", "PAP", "HPV-CERVIX", "MAM", "PSA", "FOBT", "AAA",
                        "COLONOSCOPY", "LDCT", "BMD", "HIV", "HepB screen", "HepC screen", "VDRL",
                        "chlamydia", "ghonorrhea PCR", "PHV", "Smoking", "OtherB");
        var rows = new ArrayList<CdsExportCapturedTextRowDto>();
        for (int i = 0; i < names.size(); i++) rows.add(row(i + 1, names.get(i), "0", null));
        var result = new CdsExportPreventionScopePlanner().plan(27, bundled, rows);
        assertThat(result.otherPreventions()).hasSize(19);
        assertThat(result.immunizations()).isEmpty();
        assertThat(result.representable()).isTrue();
    }

    @Test
    void shouldVerifyAnEmptyCompleteMetadataSourceStaysEmptyRatherThanInventingAZeroDateOrFact_forPreventionScopePlannerContract()
            throws Exception {
        var result = plan(List.of());
        assertThat(result.rows()).isZero();
        assertThat(result.representable()).isTrue();
        assertThat(result.immunizations()).isEmpty();
        assertThat(result.otherPreventions()).isEmpty();
    }

    @Test
    void shouldVerifyExplicitFalseAndLegacyNull_withoutAProgramAreDistinctPreservedUnrestrictedStates()
            throws Exception {
        var result = plan(List.of(row(1, "Td", "0", "999"), row(2, "Td", null, null)));
        assertThat(result.immunizations()).containsExactlyInAnyOrder(1, 2);
        assertThat(result.reviews()).isEmpty();
        assertThat(plan(List.of(row(1, "Td", "0", null))).scopeSha256())
                .isNotEqualTo(plan(List.of(row(1, "Td", null, null))).scopeSha256());
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "2", "-1", "true", ""})
    void shouldVerifyActiveOrUnknownRestrictionStatesNeverProvideProgramAuthority_forPreventionScopePlannerContract(String flag)
            throws Exception {
        var result = plan(List.of(row(1, "Td", flag, "1")));
        assertThat(result.immunizations()).isEmpty();
        assertThat(result.reviews())
                .containsEntry(1, CdsExportPreventionScopePlanner.Review.PROGRAM_SCOPE);
        assertThat(result.representable()).isFalse();
    }

    @Test
    void shouldVerifyNullRestriction_withARecordedProgramIsAmbiguousAndCannotBeCapturedAsUnrestricted()
            throws Exception {
        assertThat(plan(List.of(row(1, "Td", null, "1"))).reviews())
                .containsEntry(1, CdsExportPreventionScopePlanner.Review.PROGRAM_SCOPE);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void shouldVerifyMissingTypeRequiresReviewRatherThanBecomingAnExcludedScreening_forPreventionScopePlannerContract(String type)
            throws Exception {
        assertThat(plan(List.of(row(1, type, "0", null))).reviews())
                .containsEntry(1, CdsExportPreventionScopePlanner.Review.UNKNOWN_TYPE);
    }

    @Test
    void shouldVerifyUnknownLayoutAndUnresolvedScreeningCodeAreExplicitReviewPartitions_forPreventionScopePlannerContract() throws Exception {
        var withCode = changed(row(2, "PAP", "0", null), "snomedId", "not-in-frozen-catalogue");
        var result = plan(List.of(row(1, "future", "0", null), withCode));
        assertThat(result.reviews())
                .containsEntry(1, CdsExportPreventionScopePlanner.Review.UNSUPPORTED_LAYOUT)
                .containsEntry(
                        2, CdsExportPreventionScopePlanner.Review.UNRESOLVED_NON_IMMUNIZATION_CODE);
    }

    @Test
    void shouldVerifyDuplicateOrReorderedMetadataCannotSilentlyCollapseIntoSets_forPreventionScopePlannerContract() {
        assertThatThrownBy(() -> plan(List.of(row(1, "Td", "0", null), row(1, "Td", "0", null))))
                .isInstanceOf(IOException.class);
        assertThatThrownBy(() -> plan(List.of(row(2, "Td", "0", null), row(1, "Td", "0", null))))
                .isInstanceOf(IOException.class);
    }

    @Test
    void shouldVerifyForeignOwnersWrongKeysExtraColumnsAndNoncanonicalIdsAreRejected_forPreventionScopePlannerContract() {
        var row = row(1, "Td", "0", null);
        var fields = new TreeMap<>(row.fields());
        fields.put("extra", "value");
        for (var invalid :
                List.of(
                        changed(row, "demographic_no", "28"),
                        changed(row, "id", "01"),
                        changed(row, "programNo", "+1"),
                        new CdsExportCapturedTextRowDto(
                                "preventions", row.recordKey(), row.fields()),
                        new CdsExportCapturedTextRowDto(row.table(), "wrong", row.fields()),
                        new CdsExportCapturedTextRowDto(row.table(), row.recordKey(), fields)))
            assertThatThrownBy(() -> plan(List.of(invalid)))
                    .isInstanceOf(IOException.class)
                    .hasMessage("CDS_EXPORT_PREVENTION_SCOPE_INVALID");
    }

    @Test
    void shouldVerifyAnEarlierReviewNeverHidesAForeignOrCorruptLaterRow_forPreventionScopePlannerContract() {
        assertThatThrownBy(
                        () ->
                                plan(
                                        List.of(
                                                row(1, "unknown", "0", null),
                                                changed(
                                                        row(2, "Td", "0", null),
                                                        "demographic_no",
                                                        "28"))))
                .isInstanceOf(IOException.class);
    }

    @Test
    void shouldVerifyInputFailure_afterAnEarlierReviewPropagatesRatherThanReturningAPartialPartition() {
        Iterable<CdsExportCapturedTextRowDto> failing =
                () ->
                        new Iterator<>() {
                            boolean first = true;

                            public boolean hasNext() {
                                if (!first) throw new IllegalStateException("LATE_SOURCE_FAILURE");
                                return true;
                            }

                            public CdsExportCapturedTextRowDto next() {
                                first = false;
                                return row(1, "unknown", "0", null);
                            }
                        };
        assertThatThrownBy(
                        () -> new CdsExportPreventionScopePlanner().plan(27, classifier(), failing))
                .hasMessage("LATE_SOURCE_FAILURE");
    }

    @Test
    void shouldVerifyHashesBindTheCompleteMetadataAndClassificationDefinitions_forPreventionScopePlannerContract() throws Exception {
        var rows = List.of(row(1, "Td", "0", null));
        var result = plan(rows);
        assertThat(result.scopeSha256()).hasSize(64);
        assertThat(result.sourceManifestSha256()).isEqualTo("a".repeat(64));
        var changed =
                CdsExportPreventionClassifier.fromCaptured(
                        "b".repeat(64),
                        "<preventions><item name='Td' layout='injection' healthCanadaType='Td'/></preventions>"
                                .getBytes(StandardCharsets.UTF_8),
                        List.of());
        assertThat(new CdsExportPreventionScopePlanner().plan(27, changed, rows).scopeSha256())
                .isNotEqualTo(result.scopeSha256());
        assertThat(result.toString()).doesNotContain("27", "Td", result.scopeSha256());
    }

    private static CdsExportPreventionClassifier withCatchAll() throws IOException {
        return CdsExportPreventionClassifier.fromCaptured(
                "a".repeat(64),
                "<preventions><item name='Td' layout='injection' healthCanadaType='Td'/><item name='OtherA' layout='injection' headingName='Other' healthCanadaType=''/><item name='OtherB' layout='PAPMAM' headingName='Other'/></preventions>"
                        .getBytes(StandardCharsets.UTF_8),
                List.of());
    }

    // Review finding F10: the importer files a vaccine with an absent or unconfigured
    // ImmunizationType as OtherA, which the classification alone cannot prove.
    @Test
    void shouldSelectAReceiptProvenImportedCatchAllVaccine_asAnImmunization() throws Exception {
        var asked = new ArrayList<Set<Integer>>();
        var result =
                new CdsExportPreventionScopePlanner()
                        .plan(
                                27,
                                withCatchAll(),
                                List.of(row(1, "OtherA", null, null), row(2, "Td", "0", null)),
                                candidates -> {
                                    asked.add(candidates);
                                    return Set.of(1);
                                });
        assertThat(asked).containsExactly(Set.of(1));
        assertThat(result.immunizations()).containsExactlyInAnyOrder(1, 2);
        assertThat(result.reviews()).isEmpty();
        assertThat(result.representable()).isTrue();
        assertThat(result.scopeSha256())
                .isNotEqualTo(
                        new CdsExportPreventionScopePlanner()
                                .plan(
                                        27,
                                        withCatchAll(),
                                        List.of(
                                                row(1, "OtherA", null, null),
                                                row(2, "Td", "0", null)))
                                .scopeSha256());
    }

    @Test
    void shouldHoldANativeCatchAllRow_withCategoryNotProven() throws Exception {
        var result =
                new CdsExportPreventionScopePlanner()
                        .plan(
                                27,
                                withCatchAll(),
                                List.of(row(1, "OtherA", "0", null), row(2, "unknown", "0", null)),
                                candidates -> Set.of());
        assertThat(result.reviews())
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of(
                                1, CdsExportPreventionScopePlanner.Review.CATEGORY_NOT_PROVEN,
                                2, CdsExportPreventionScopePlanner.Review.UNKNOWN_TYPE));
        assertThat(result.immunizations()).isEmpty();
        assertThat(result.representable()).isFalse();
    }

    @Test
    void shouldNeverLetAnImportReceiptOverrideAProgramRestrictionOrAPlacedRow_forPreventionScopePlannerContract()
            throws Exception {
        var result =
                new CdsExportPreventionScopePlanner()
                        .plan(
                                27,
                                withCatchAll(),
                                List.of(
                                        row(1, "OtherA", "1", "5"),
                                        row(2, "OtherB", "0", null),
                                        row(3, "Td", "0", null)),
                                candidates -> {
                                    throw new AssertionError("No row needs a receipt");
                                });
        assertThat(result.reviews())
                .containsExactlyEntriesOf(
                        Map.of(1, CdsExportPreventionScopePlanner.Review.PROGRAM_SCOPE));
        assertThat(result.otherPreventions()).containsExactly(2);
        assertThat(result.immunizations()).containsExactly(3);
    }

    @Test
    void shouldRefuseAReceiptProofForARowThatWasNotACandidate_forPreventionScopePlannerContract() {
        assertThatThrownBy(
                        () ->
                                new CdsExportPreventionScopePlanner()
                                        .plan(
                                                27,
                                                withCatchAll(),
                                                List.of(
                                                        row(1, "OtherA", "0", null),
                                                        row(2, "OtherB", "0", null)),
                                                candidates -> Set.of(1, 2)))
                .isInstanceOf(IOException.class)
                .hasMessage("CDS_EXPORT_PREVENTION_SCOPE_INVALID");
    }

    @Test
    void shouldRefuseACorruptLaterRow_beforeAnyReceiptIsConsulted() {
        assertThatThrownBy(
                        () ->
                                new CdsExportPreventionScopePlanner()
                                        .plan(
                                                27,
                                                withCatchAll(),
                                                List.of(
                                                        row(1, "OtherA", "0", null),
                                                        changed(
                                                                row(2, "Td", "0", null),
                                                                "demographic_no",
                                                                "28")),
                                                candidates -> {
                                                    throw new AssertionError("Consulted early");
                                                }))
                .isInstanceOf(IOException.class)
                .hasMessage("CDS_EXPORT_PREVENTION_SCOPE_INVALID");
    }

    private static CdsExportCapturedTextRowDto row(
            int id, String type, String flag, String program) {
        var values = new TreeMap<String, String>();
        values.put("id", Integer.toString(id));
        values.put("demographic_no", "27");
        values.put("prevention_type", type);
        values.put("snomedId", null);
        values.put("restrictToProgram", flag);
        values.put("programNo", program);
        return new CdsExportCapturedTextRowDto(
                "preventions_scope", CdsExportPatientTableSourceReader.key(id), values);
    }

    private static CdsExportCapturedTextRowDto changed(
            CdsExportCapturedTextRowDto row, String key, String value) {
        var fields = new TreeMap<>(row.fields());
        fields.put(key, value);
        return new CdsExportCapturedTextRowDto(row.table(), row.recordKey(), fields);
    }
}
