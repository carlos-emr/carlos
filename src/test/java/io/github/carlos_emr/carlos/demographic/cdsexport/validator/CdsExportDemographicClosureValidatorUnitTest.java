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
package io.github.carlos_emr.carlos.demographic.cdsexport.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.*;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit") @Tag("cds-export")
class CdsExportDemographicClosureValidatorUnitTest {
    private static final String HASH = "a".repeat(64), SOURCE = "b".repeat(64);
    private static final CdsExportNativeFieldKeyDto KEY = new CdsExportNativeFieldKeyDto("demographic", "0000000027", "first_name");
    private static final CdsExportFieldAssessmentDto MAPPED = new CdsExportFieldAssessmentDto(CdsExportFieldAssessmentDto.Presence.VALUE,
            CdsExportFieldAssessmentDto.Outcome.MAPPED, "Names/LegalName/FirstName", "EXACT_SOURCE");
    private final CdsExportDemographicClosureValidator validator = new CdsExportDemographicClosureValidator();
    private final List<CdsExportCapturedTextRowDto> rows = List.of(new CdsExportCapturedTextRowDto("demographic", "0000000027", Map.of("first_name", "Ada")));
    private CdsExportDemographicRetainedCoreReconciler.Result result(Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields,
            Set<CdsExportNativeFieldKeyDto> nativeDeferred, Set<String> sourceDeferred, Set<String> decided,
            List<CdsExportRetainedSectionWriter.Change> changes) {
        return new CdsExportDemographicRetainedCoreReconciler.Result(HASH, "parent", decided, sourceDeferred, fields, nativeDeferred, Set.of(), changes);
    }
    private CdsExportDemographicRetainedCoreReconciler.Result complete() { return result(Map.of(KEY, MAPPED), Set.of(), Set.of(), Set.of(SOURCE), List.of()); }
    @Test void shouldRequireExactSourceAndNativeIdentityClosure_beforeReturningAReceipt() throws Exception {
        var receipt = validator.validate(HASH, rows, Set.of(SOURCE), complete(), 100, () -> {});
        assertThat(receipt.nativeFields()).isEqualTo(1); assertThat(receipt.retainedSourceFields()).isEqualTo(1);
        assertThat(receipt.toString()).doesNotContain(HASH, "Ada");
    }
    @Test void shouldRefuseADeferredField_evenWhenTheAssemblyClaimsOtherFieldsAreComplete() {
        var result = result(Map.of(KEY, MAPPED), Set.of(new CdsExportNativeFieldKeyDto("demographiccust", "27", "cust1")), Set.of(), Set.of(SOURCE), List.of());
        assertThatThrownBy(() -> validator.validate(HASH, rows, Set.of(SOURCE), result, 100, () -> {})).hasMessage("CDS_EXPORT_DEMOGRAPHIC_CLOSURE_SCOPE_INVALID");
    }
    @Test void shouldRefuseASubstitutedSourceId_evenWhenTheCountsMatch() {
        var result = result(Map.of(KEY, MAPPED), Set.of(), Set.of(), Set.of("c".repeat(64)), List.of());
        assertThatThrownBy(() -> validator.validate(HASH, rows, Set.of(SOURCE), result, 100, () -> {})).hasMessage("CDS_EXPORT_DEMOGRAPHIC_CLOSURE_SCOPE_INVALID");
    }
    @Test void shouldRefuseASubstitutedNativeField_evenWhenItsValueAndCountMatch() {
        var other = new CdsExportNativeFieldKeyDto("demographic", "0000000028", "first_name");
        var result = result(Map.of(other, MAPPED), Set.of(), Set.of(), Set.of(SOURCE), List.of());
        assertThatThrownBy(() -> validator.validate(HASH, rows, Set.of(SOURCE), result, 100, () -> {})).hasMessage("CDS_EXPORT_FIELD_ASSESSMENT_SCOPE_INVALID");
    }
    @Test void shouldRefuseAReviewDecision_withoutReturningAClosureReceipt() {
        var blocked = new CdsExportFieldAssessmentDto(CdsExportFieldAssessmentDto.Presence.VALUE, CdsExportFieldAssessmentDto.Outcome.REQUIRES_REVIEW, "", "UNMAPPED");
        var result = result(Map.of(KEY, blocked), Set.of(), Set.of(), Set.of(SOURCE), List.of());
        assertThatThrownBy(() -> validator.validate(HASH, rows, Set.of(SOURCE), result, 100, () -> {})).hasMessage("CDS_EXPORT_DEMOGRAPHIC_CLOSURE_REQUIRES_REVIEW");
    }
    @Test void shouldRefuseDuplicateSourceReplacementClaims_betweenXmlChanges() {
        var change = new CdsExportRetainedSectionWriter.Change("Names[1]", Set.of(SOURCE), xml -> {});
        var result = result(Map.of(KEY, MAPPED), Set.of(), Set.of(), Set.of(SOURCE), List.of(change, change));
        assertThatThrownBy(() -> validator.validate(HASH, rows, Set.of(SOURCE), result, 100, () -> {})).hasMessage("CDS_EXPORT_DEMOGRAPHIC_CLOSURE_SCOPE_INVALID");
    }
    @Test void shouldRefuseChangedCaptureIdentity_andEmptyInventories() {
        assertThatThrownBy(() -> validator.validate("c".repeat(64), rows, Set.of(SOURCE), complete(), 100, () -> {})).hasMessage("CDS_EXPORT_DEMOGRAPHIC_CLOSURE_SCOPE_INVALID");
        assertThatThrownBy(() -> validator.validate(HASH, List.of(), Set.of(SOURCE), complete(), 100, () -> {})).hasMessage("CDS_EXPORT_DEMOGRAPHIC_CLOSURE_SCOPE_INVALID");
        assertThatThrownBy(() -> validator.validate(HASH, rows, Set.of(), complete(), 100, () -> {})).hasMessage("CDS_EXPORT_DEMOGRAPHIC_CLOSURE_SCOPE_INVALID");
    }
    @Test void shouldEnforceFieldBounds_andPropagateFinalCancellation() {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        assertThatThrownBy(() -> validator.validate(HASH, rows, Set.of(SOURCE), complete(), 100,
                () -> { if (calls.incrementAndGet() == 5) throw new java.util.concurrent.CancellationException("CANCELLED"); }))
                .isInstanceOf(java.util.concurrent.CancellationException.class);
    }
}
