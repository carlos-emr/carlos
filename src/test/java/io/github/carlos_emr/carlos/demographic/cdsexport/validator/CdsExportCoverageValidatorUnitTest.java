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

import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportCoverageDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportDispositionDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportDispositionDto.Blocker;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportDispositionDto.Disposition;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportIdentityDto;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
@Tag("cds-export")
class CdsExportCoverageValidatorUnitTest {
    private static final String CAPTURE = "f".repeat(64);
    private final CdsExportCoverageValidator validator = new CdsExportCoverageValidator();

    @Test void shouldVerifyEachOccurrence_whenInventoryAndDispositionIdentitiesMatch() {
        var result = reconcile(List.of(id(1), id(2)), List.of(emitted(1), emitted(2)));
        assertThat(result.sourceCoverageComplete()).isTrue();
        assertThat(result.expected()).isEqualTo(2);
        assertThat(result.emitted()).isEqualTo(2);
        assertThat(result.issues()).isEmpty();
    }

    @Test void shouldDetectMissingAndInventedOccurrences_whenOverallCountsMatch() {
        var result = reconcile(List.of(id(1), id(2)), List.of(emitted(1), emitted(3)));
        assertThat(result.sourceCoverageComplete()).isFalse();
        assertThat(result.emitted()).isEqualTo(1);
        assertThat(result.missing()).isEqualTo(1);
        assertThat(result.unexpected()).isEqualTo(1);
        assertThat(result.issues()).extracting(CdsExportCoverageDto.IssueDto::issue)
                .containsExactly(CdsExportCoverageDto.Issue.MISSING, CdsExportCoverageDto.Issue.UNEXPECTED);
    }

    @Test void shouldRefuseDuplicateDisposition_whenItCouldHideAnOmittedOccurrence() {
        assertThatThrownBy(() -> reconcile(List.of(id(1), id(2)), List.of(emitted(1), emitted(1))))
                .hasMessage("CDS_EXPORT_COVERAGE_DUPLICATE_IDENTITY");
    }

    @Test void shouldRefuseDuplicateInventory_insteadOfTreatingItAsTwoClinicalFacts() {
        assertThatThrownBy(() -> reconcile(List.of(id(1), id(1)), List.of(emitted(1))))
                .hasMessage("CDS_EXPORT_COVERAGE_DUPLICATE_IDENTITY");
    }

    @Test void shouldRejectUnorderedInputs_beforeReturningACompletionResult() {
        assertThatThrownBy(() -> reconcile(List.of(id(2), id(1)), List.of(emitted(1), emitted(2))))
                .hasMessage("CDS_EXPORT_COVERAGE_ORDER_INVALID");
        assertThatThrownBy(() -> reconcile(List.of(id(1), id(2)), List.of(emitted(2), emitted(1))))
                .hasMessage("CDS_EXPORT_COVERAGE_ORDER_INVALID");
    }

    @Test void shouldAccountForBlockedSources_withoutReportingCompleteCoverage() {
        var blocked = new CdsExportDispositionDto(CAPTURE, id(2), Disposition.BLOCKED, null, Blocker.RENDER_INCOMPLETE);
        var result = reconcile(List.of(id(1), id(2)), List.of(emitted(1), blocked));
        assertThat(result.expected()).isEqualTo(2);
        assertThat(result.emitted()).isEqualTo(1);
        assertThat(result.blocked()).isEqualTo(1);
        assertThat(result.missing()).isZero();
        assertThat(result.sourceCoverageComplete()).isFalse();
    }

    @Test void shouldRejectEmptyInventory_asEvidenceOfCompleteExtraction() {
        assertThat(reconcile(List.of(), List.of()).sourceCoverageComplete()).isFalse();
    }

    @Test void shouldRejectStaleDispositions_evenWhenTheirOccurrenceIdentitiesMatch() {
        var stale = new CdsExportDispositionDto("e".repeat(64), id(1), Disposition.EMITTED, "a".repeat(64), null);
        assertThatThrownBy(() -> reconcile(List.of(id(1)), List.of(stale)))
                .hasMessage("CDS_EXPORT_CAPTURE_MISMATCH");
    }

    @Test void shouldBindCoverageToTheCapture_thatProvidedItsInventory() {
        assertThat(reconcile(List.of(id(1)), List.of(emitted(1))).captureDigest()).isEqualTo(CAPTURE);
        assertThatThrownBy(() -> validator.validate("", List.of(id(1)).iterator(),
                List.of(emitted(1)).iterator(), 10, () -> {})).hasMessage("CDS_EXPORT_CAPTURE_INVALID");
    }

    @Test void shouldPreserveThreadInterruption_withoutReturningAResult() {
        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(() -> reconcile(List.of(id(1)), List.of(emitted(1))))
                    .hasMessage("CDS_EXPORT_COVERAGE_INTERRUPTED");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @Test void shouldKeepCompleteCounts_whenIssuePreviewsReachTheirBound() {
        var identities = IntStream.rangeClosed(1, 100).mapToObj(this::id).toList();
        var result = reconcile(identities, List.of());
        assertThat(result.missing()).isEqualTo(100);
        assertThat(result.issues()).hasSize(CdsExportCoverageDto.MAXIMUM_ISSUE_PREVIEWS);
        assertThat(result.issuePreviewsLimited()).isTrue();
    }

    @Test void shouldEnforceBothInputLimits_withoutReturningPartialSuccess() {
        assertThatThrownBy(() -> validator.validate(CAPTURE, List.of(id(1), id(2)).iterator(),
                List.of(emitted(1)).iterator(), 1, () -> {}))
                .hasMessage("CDS_EXPORT_COVERAGE_LIMIT_EXCEEDED");
        assertThatThrownBy(() -> validator.validate(CAPTURE, List.of(id(1)).iterator(),
                List.of(emitted(1), emitted(2)).iterator(), 1, () -> {}))
                .hasMessage("CDS_EXPORT_COVERAGE_LIMIT_EXCEEDED");
    }

    @Test void shouldPropagateIteratorFailures_insteadOfReturningAZeroOrPartialCount() {
        Iterator<CdsExportDispositionDto> failing = new Iterator<>() {
            public boolean hasNext() { throw new IllegalStateException("fixture storage unavailable"); }
            public CdsExportDispositionDto next() { throw new AssertionError("unreachable"); }
        };
        assertThatThrownBy(() -> validator.validate(CAPTURE, List.of(id(1)).iterator(), failing, 100, () -> {}))
                .hasMessage("fixture storage unavailable");
    }

    @Test void shouldCheckOwnershipAndCancellation_duringLazyReconciliation() {
        AtomicInteger calls = new AtomicInteger();
        Runnable checkpoint = () -> {
            if (calls.incrementAndGet() == 20) throw new IllegalStateException("fixture ownership lost");
        };
        assertThatThrownBy(() -> validator.validate(CAPTURE,
                IntStream.rangeClosed(1, 10000).mapToObj(this::id).iterator(),
                IntStream.rangeClosed(1, 10000).mapToObj(this::emitted).iterator(), 10000, checkpoint))
                .hasMessage("fixture ownership lost");
        assertThat(calls).hasValue(20);
    }

    @Test void shouldReconcileLargeLazyInputs_withoutCollectingTheInventory() {
        var result = validator.validate(CAPTURE,
                IntStream.rangeClosed(1, 10000).mapToObj(this::id).iterator(),
                IntStream.rangeClosed(1, 10000).mapToObj(this::emitted).iterator(), 10000, () -> {});
        assertThat(result.expected()).isEqualTo(10000);
        assertThat(result.sourceCoverageComplete()).isTrue();
    }

    @Test void shouldKeepPatientsAndCategoriesDistinct_whenOccurrenceKeysMatch() {
        var first = new CdsExportIdentityDto("1".repeat(64), CdsCategory.CLINICAL_NOTES, "a".repeat(64));
        var otherCategory = new CdsExportIdentityDto("1".repeat(64), CdsCategory.REPORTS, "a".repeat(64));
        var otherPatient = new CdsExportIdentityDto("2".repeat(64), CdsCategory.CLINICAL_NOTES, "a".repeat(64));
        assertThat(first.compareTo(otherCategory)).isNegative();
        assertThat(first.compareTo(otherPatient)).isNegative();
        assertThat(List.of(first, otherCategory, otherPatient)).doesNotHaveDuplicates();
    }

    private CdsExportCoverageDto reconcile(List<CdsExportIdentityDto> expected,
            List<CdsExportDispositionDto> actual) {
        return validator.validate(CAPTURE, expected.iterator(), actual.iterator(), 100000, () -> {});
    }

    private CdsExportIdentityDto id(int number) {
        return new CdsExportIdentityDto("1".repeat(64), CdsCategory.CLINICAL_NOTES,
                String.format(java.util.Locale.ROOT, "%064x", number));
    }

    private CdsExportDispositionDto emitted(int number) {
        return new CdsExportDispositionDto(CAPTURE, id(number), Disposition.EMITTED, "a".repeat(64), null);
    }
}
