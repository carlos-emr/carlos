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
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.util.*;
import org.junit.jupiter.api.*;

@Tag("unit")
@Tag("cds-export")
class CdsExportNewCategoryOccurrenceServiceUnitTest {
    private static final String RUN = "11111111-1111-4111-8111-111111111111",
            HASH = "a".repeat(64),
            UNIT = "b".repeat(64),
            ADDRESS = RUN + ":" + UNIT;
    private final Map<String, CdsExportCapturedTextRowDto> units = new LinkedHashMap<>();
    private final Map<String, List<CdsExportRetainedIndexDto.Reference>> references =
            new LinkedHashMap<>();
    private final Map<String, List<CdsExportRetainedReceiptVerificationDto.CheckedReference>>
            checked = new LinkedHashMap<>();
    private final Set<String> applied = new HashSet<>(),
            unchanged = new HashSet<>(),
            review = new HashSet<>();

    @BeforeEach
    void setup() {
        add(
                UNIT,
                "demographic",
                "27",
                CdsExportRetainedReceiptVerificationDto.Reason.UNCHANGED_ROW);
    }

    private void add(
            String unit,
            String table,
            String id,
            CdsExportRetainedReceiptVerificationDto.Reason reason) {
        String address = RUN + ":" + unit, key = CdsImportKeys.hash(RUN, unit);
        var expected = new CdsExportRetainedIndexDto.Reference(table, id, HASH);
        units.put(
                key,
                new CdsExportCapturedTextRowDto(
                        "cds_import_unit",
                        key,
                        Map.of(
                                "run_id",
                                RUN,
                                "unit_key",
                                unit,
                                "category",
                                "NewCategory",
                                "state",
                                "COMMITTED")));
        applied.add(address);
        references.put(address, List.of(expected));
        String current =
                reason == CdsExportRetainedReceiptVerificationDto.Reason.UNCHANGED_ROW
                        ? HASH
                        : reason == CdsExportRetainedReceiptVerificationDto.Reason.CHANGED_ROW
                                ? "c".repeat(64)
                                : null;
        checked.put(
                address,
                List.of(
                        new CdsExportRetainedReceiptVerificationDto.CheckedReference(
                                expected, current, reason)));
        unchanged.remove(address);
        review.remove(address);
        (reason == CdsExportRetainedReceiptVerificationDto.Reason.UNCHANGED_ROW
                        ? unchanged
                        : review)
                .add(address);
    }

    private CdsExportRetainedIndexDto index(String hash) {
        return new CdsExportRetainedIndexDto(
                hash,
                units.size(),
                100,
                Map.of("cds_import_unit", units),
                applied,
                references,
                Map.of());
    }

    private List<String> parents(int maximum, Runnable checkpoint) throws Exception {
        return CdsExportNewCategoryOccurrenceService.parents(
                27,
                index(HASH),
                new CdsExportRetainedReceiptVerificationDto(HASH, checked, unchanged, review),
                maximum,
                checkpoint);
    }

    @Test
    void shouldBindTheExactCurrentPatientAssociation_withoutASeparateNativeClinicalProjection()
            throws Exception {
        assertThat(parents(10, () -> {})).containsExactly(ADDRESS);
    }

    @Test
    void shouldKeepSourceOnlySupplementalValues_whenUnrelatedDemographicFieldsChanged()
            throws Exception {
        add(UNIT, "demographic", "27", CdsExportRetainedReceiptVerificationDto.Reason.CHANGED_ROW);
        assertThat(parents(10, () -> {})).containsExactly(ADDRESS);
    }

    @Test
    void shouldRefuseAForeignPatientAssociation_insteadOfReattachingItsSourceFields() {
        add(
                UNIT,
                "demographic",
                "28",
                CdsExportRetainedReceiptVerificationDto.Reason.UNCHANGED_ROW);
        assertThatThrownBy(() -> parents(10, () -> {}))
                .hasMessage("CDS_EXPORT_NEW_CATEGORY_OCCURRENCE_INVALID");
    }

    @Test
    void shouldRefuseAnUnexpectedNativeTarget_insteadOfTreatingItAsUnprojectedData() {
        add(UNIT, "drugs", "27", CdsExportRetainedReceiptVerificationDto.Reason.UNCHANGED_ROW);
        assertThatThrownBy(() -> parents(10, () -> {}))
                .hasMessage("CDS_EXPORT_NEW_CATEGORY_OCCURRENCE_INVALID");
    }

    @Test
    void shouldRefuseAMissingCurrentPatient_withoutSilentlyRetainingItsHistoricalXml() {
        add(
                UNIT,
                "demographic",
                "27",
                CdsExportRetainedReceiptVerificationDto.Reason.ROW_NOT_IN_CAPTURE);
        assertThatThrownBy(() -> parents(10, () -> {})).hasMessage("CDS_EXPORT_UNMAPPED_DATA");
    }

    @Test
    void shouldRefuseUnavailableNativeEvidence_insteadOfClaimingUnchangedOwnership() {
        add(
                UNIT,
                "demographic",
                "27",
                CdsExportRetainedReceiptVerificationDto.Reason.RECEIPT_METADATA_UNAVAILABLE);
        assertThatThrownBy(() -> parents(10, () -> {})).hasMessage("CDS_EXPORT_UNMAPPED_DATA");
    }

    @Test
    void shouldRefuseChangedGenerationEvidence_evenWhenThePatientNumberMatches() {
        var result =
                new CdsExportRetainedReceiptVerificationDto(
                        "d".repeat(64), checked, unchanged, review);
        assertThatThrownBy(
                        () ->
                                CdsExportNewCategoryOccurrenceService.parents(
                                        27, index(HASH), result, 10, () -> {}))
                .hasMessage("CDS_EXPORT_NEW_CATEGORY_OCCURRENCE_INVALID");
    }

    @Test
    void shouldRefuseAnIncompleteUnitProofSet_beforeReturningAnyParent() {
        var proof = new CdsExportRetainedReceiptVerificationDto(HASH, Map.of(), Set.of(), Set.of());
        assertThatThrownBy(
                        () ->
                                CdsExportNewCategoryOccurrenceService.parents(
                                        27, index(HASH), proof, 10, () -> {}))
                .hasMessage("CDS_EXPORT_NEW_CATEGORY_OCCURRENCE_INVALID");
    }

    @Test
    void shouldKeepDistinctOriginalOccurrences_inStableSourceOrder() throws Exception {
        add(
                "c".repeat(64),
                "demographic",
                "27",
                CdsExportRetainedReceiptVerificationDto.Reason.UNCHANGED_ROW);
        assertThat(parents(10, () -> {})).containsExactly(ADDRESS, RUN + ":" + "c".repeat(64));
    }

    @Test
    void shouldRefuseBounds_withoutACompleteParentSet() {
        add(
                "c".repeat(64),
                "demographic",
                "27",
                CdsExportRetainedReceiptVerificationDto.Reason.UNCHANGED_ROW);
        assertThatThrownBy(() -> parents(1, () -> {})).hasMessage("CDS_EXPORT_RESOURCE_LIMIT");
    }

    @Test
    void shouldRefuseDuplicateMetadata_forOneOriginalParent() {
        units.put("d".repeat(64), units.values().iterator().next());
        assertThatThrownBy(() -> parents(10, () -> {}))
                .hasMessage("CDS_EXPORT_NEW_CATEGORY_OCCURRENCE_INVALID");
    }

    @Test
    void shouldExcludeUnappliedMetadata_withoutInventingAnOccurrence() throws Exception {
        applied.clear();
        checked.clear();
        unchanged.clear();
        review.clear();
        assertThat(parents(10, () -> {})).isEmpty();
    }

    @Test
    void shouldPropagateCancellation_beforeAParentClaim() {
        assertThatThrownBy(
                        () ->
                                parents(
                                        10,
                                        () -> {
                                            throw new java.util.concurrent.CancellationException(
                                                    "CANCELLED");
                                        }))
                .hasMessage("CANCELLED");
    }
}
