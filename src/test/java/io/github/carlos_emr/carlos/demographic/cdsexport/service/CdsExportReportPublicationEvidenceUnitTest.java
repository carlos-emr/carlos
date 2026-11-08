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
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsReportNativeDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Tag("unit") @Tag("cds-export")
class CdsExportReportPublicationEvidenceUnitTest {
    private static final String RUN = "11111111-1111-4111-8111-111111111111", FILE = "22222222-2222-4222-8222-222222222222", UNIT = "a".repeat(64), HASH = "b".repeat(64), ADDRESS = RUN + ":" + UNIT;
    private final CdsReportNativeDto.Variant variant = new CdsReportNativeDto.Variant("embedded", "txt", "text/plain;charset=UTF-8", HASH, 17, false, List.of());
    private Map<String, String> file;
    private Map<String, String> link;
    private List<CdsExportRetainedIndexDto.Reference> refs;
    private String linkId;
    @BeforeEach void setup() {
        String location = "/private/native-documents/cds-" + UNIT + "-embedded-" + HASH + ".txt"; linkId = CdsImportKeys.hash(RUN, UNIT, FILE);
        file = new HashMap<>(Map.of("id", FILE, "run_id", RUN, "location", location, "location_key", CdsImportKeys.hash(location), "digest", HASH, "byte_length", "17", "generation", "1", "state", "PUBLISHED", "verified_epoch", "9"));
        link = new HashMap<>(Map.of("id", linkId, "run_id", RUN, "unit_key", UNIT, "file_id", FILE));
        refs = new ArrayList<>(List.of(new CdsExportRetainedIndexDto.Reference("document", "7", HASH), new CdsExportRetainedIndexDto.Reference("ctl_document", "27_7", HASH), new CdsExportRetainedIndexDto.Reference("cds_import_file", FILE, HASH), new CdsExportRetainedIndexDto.Reference("cds_import_file_reference", linkId, HASH)));
    }
    private CdsExportRetainedIndexDto index() { return new CdsExportRetainedIndexDto(HASH, 2, 1024, Map.of("cds_import_file", Map.of(FILE, new CdsExportCapturedTextRowDto("cds_import_file", FILE, file)), "cds_import_file_reference", Map.of(linkId, new CdsExportCapturedTextRowDto("cds_import_file_reference", linkId, link))), Set.of(ADDRESS), Map.of(ADDRESS, refs), Map.of()); }
    @Test void shouldAcceptASealedRepublishedGeneration_withoutAssumingItWasTheFirstAttempt() { file.put("generation", "2"); assertThatCode(this::require).doesNotThrowAnyException(); }
    @ParameterizedTest @ValueSource(strings = {"0", "-1", "02", "9223372036854775808"})
    void shouldRefuseInvalidOrNoncanonicalPublicationGenerations(String value) { file.put("generation", value); assertThatThrownBy(this::require).hasMessage("CDS_EXPORT_REPORT_PUBLICATION_EVIDENCE_INVALID"); }
    private void require() throws Exception { CdsExportReportPublicationEvidence.require(ADDRESS, index(), refs, Map.of("embedded", variant), () -> {}); }
    @Test void shouldCloseTheWholeDocumentAndDurablePublicationReceipt() { assertThatCode(this::require).doesNotThrowAnyException(); }
    @ParameterizedTest @ValueSource(strings = {"id", "location_key", "digest", "byte_length", "generation", "state"})
    void shouldRefuseChangedPublicationEvidence_beforeUsingAnyNativeBytes(String column) { file.put(column, "Changed"); assertThatThrownBy(this::require).hasMessage("CDS_EXPORT_REPORT_PUBLICATION_EVIDENCE_INVALID"); }
    @ParameterizedTest @ValueSource(strings = {"id", "run_id", "unit_key", "file_id"})
    void shouldRefuseAFileReferenceFromAnotherUnitOrOwner(String column) { link.put(column, "Changed"); assertThatThrownBy(this::require).hasMessage("CDS_EXPORT_REPORT_PUBLICATION_EVIDENCE_INVALID"); }
    @Test void shouldRefuseAnUnboundPublicationFilename() { file.put("location", "/private/native-documents/unrelated.txt"); file.put("location_key", CdsImportKeys.hash(file.get("location"))); assertThatThrownBy(this::require).hasMessage("CDS_EXPORT_REPORT_PUBLICATION_EVIDENCE_INVALID"); }
    @Test void shouldRefuseRelativeOrNoncanonicalNativeLocations() { file.put("location", "private/native.txt"); assertThatThrownBy(this::require).hasMessage("CDS_EXPORT_REPORT_PUBLICATION_EVIDENCE_INVALID"); }
    @Test void shouldRefuseMissingOrAdditionalEffects_insteadOfIgnoringThem() { refs.removeLast(); assertThatThrownBy(this::require).hasMessage("CDS_EXPORT_REPORT_PUBLICATION_EVIDENCE_INVALID"); }
    @Test void shouldRefuseDuplicateReceiptEffects_insteadOfCountingThemTwice() { refs.set(3, refs.get(2)); assertThatThrownBy(this::require).hasMessage("CDS_EXPORT_REPORT_PUBLICATION_EVIDENCE_INVALID"); }
    @Test void shouldPreserveTheOriginalPublishedFileOwner_withoutRestampingInheritedMetadata() { file.put("run_id", "33333333-3333-4333-8333-333333333333"); file.put("verified_epoch", "100"); assertThatCode(this::require).doesNotThrowAnyException(); }
    @Test void shouldPropagateCancellation_beforeInspectingTheCapturedReceipt() { assertThatThrownBy(() -> CdsExportReportPublicationEvidence.require(ADDRESS, null, null, null, () -> { throw new java.util.concurrent.CancellationException("CANCELLED"); })).hasMessage("CANCELLED"); }
}
