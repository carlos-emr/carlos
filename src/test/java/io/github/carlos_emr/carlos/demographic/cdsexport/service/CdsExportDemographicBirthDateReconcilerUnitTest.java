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
import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import java.io.*;
import java.util.*;
import org.junit.jupiter.api.*;

@Tag("unit")
@Tag("cds-export")
class CdsExportDemographicBirthDateReconcilerUnitTest {
    private CdsExportDemographicProjectionEvidenceUnitTest fixture;
    private boolean active = true;

    @BeforeEach
    void setup() {
        fixture = new CdsExportDemographicProjectionEvidenceUnitTest();
        fixture.setup();
    }

    @Test
    void anUnchangedDateRetainsItsEffectiveSourceValue() throws Exception {
        var result = reconcile("1980-01-02");
        assertThat(result.representable()).isTrue();
        assertThat(result.wireValue()).isEqualTo("1980-01-02");
        assertThat(result.changes()).isEmpty();
        assertThat(result.fields()).hasSize(3);
        assertThat(result.toString()).doesNotContain("1980", "27", result.unitAddress());
    }

    @Test
    void anUnchangedSyntheticCopyNeverRestoresTheUnshiftedOriginalBirthDate() throws Exception {
        fixture.preparedBirth("2000-01-02");
        var result = reconcile("1980-01-02");
        assertThat(result.wireValue()).isEqualTo("2000-01-02");
        assertThat(result.rule()).isEqualTo("VERIFIED_PREPARED_EFFECTIVE_BIRTH_DATE");
        assertThat(result.changes()).hasSize(1);
        assertThat(result.changes().getFirst().expectedPriorFields())
                .containsExactly(result.sourceField().id());
    }

    @Test
    void aLaterNativeEditIsNotShiftedASecondTime() throws Exception {
        fixture.preparedBirth("2000-01-02");
        fixture.currentBirth("2001", "01", "02");
        var result = reconcile("1980-01-02");
        assertThat(result.wireValue()).isEqualTo("2001-01-02");
        assertThat(result.rule()).isEqualTo("EXACT_CURRENT_NATIVE_BIRTH_DATE");
    }

    @Test
    void anAcceptedLogicalSourceCorrectionThatAlreadyMatchesThePreparationIsNotShiftedAgain()
            throws Exception {
        fixture.preparedBirth("2000-01-02");
        var result = reconcile("2000-01-02");
        assertThat(result.wireValue()).isEqualTo("2000-01-02");
        assertThat(result.changes()).isEmpty();
    }

    @Test
    void anUnchangedOriginalOffsetIsPreserved() throws Exception {
        var result = reconcile("1980-01-02-05:00");
        assertThat(result.wireValue()).isEqualTo("1980-01-02-05:00");
        assertThat(result.changes()).isEmpty();
    }

    @Test
    void aPreparedSyntheticShiftPreservesTheOriginalOffset() throws Exception {
        fixture.preparedBirth("2000-01-02");
        assertThat(reconcile("1980-01-02-05:00").wireValue()).isEqualTo("2000-01-02-05:00");
    }

    @Test
    void anEditedNativeDateWithAnUnprojectedSourceOffsetRequiresReview() throws Exception {
        fixture.currentBirth("1981", "01", "02");
        var result = reconcile("1980-01-02Z");
        assertThat(result.representable()).isFalse();
        assertThat(result.wireValue()).isNull();
        assertThatThrownBy(result::changes)
                .hasMessage("CDS_EXPORT_DEMOGRAPHIC_BIRTH_DATE_REQUIRES_REVIEW");
    }

    @Test
    void aPaddingOnlyNativeEditDoesNotEraseAnUnchangedSourceOffset() throws Exception {
        fixture.currentBirth("1980", "1", "2");
        var result = reconcile("1980-01-02Z");
        assertThat(result.wireValue()).isEqualTo("1980-01-02Z");
        assertThat(result.rule()).isEqualTo("VERIFIED_EQUIVALENT_NATIVE_BIRTH_CALENDAR");
    }

    @Test
    void aMatchedExistingChartsDifferentBirthDateCannotBeOverwrittenByReplayingSource()
            throws Exception {
        fixture.oldBirth("1979", "01", "02");
        var result = reconcile("1980-01-02");
        assertThat(result.representable()).isFalse();
        assertThat(result.rule()).isEqualTo("PREPARED_NATIVE_BIRTH_PROJECTION_NOT_PROVEN");
    }

    @Test
    void anInvalidNativeCalendarHasNoHistoricalFallback() throws Exception {
        fixture.currentBirth("1980", "02", "30");
        var result = reconcile("1980-01-02");
        assertThat(result.representable()).isFalse();
        assertThat(result.rule()).isEqualTo("CURRENT_NATIVE_BIRTH_DATE_REQUIRES_REVIEW");
    }

    @Test
    void anInvalidTypedSourceDateCannotCreateAnEffectiveBirthDate() {
        assertThatThrownBy(() -> reconcile("1980-02-30"))
                .hasMessage("CDS_EXPORT_DEMOGRAPHIC_BIRTH_SOURCE_INVALID");
    }

    @Test
    void missingSourceFieldsAndAnExpiredValueScopeFailClosed() throws Exception {
        var graph = fixture.withBirthSource("1980-01-02");
        String address = graph.index().nativeParents().values().iterator().next();
        assertThatThrownBy(
                        () ->
                                new CdsExportDemographicBirthDateReconciler()
                                        .reconcile(
                                                27,
                                                address,
                                                graph.index(),
                                                graph.checked(),
                                                List.of(),
                                                graph.current(),
                                                graph.witness(),
                                                values(),
                                                () -> {}))
                .hasMessage("CDS_EXPORT_DEMOGRAPHIC_RETAINED_ASSEMBLY_SCOPE_INVALID");
        active = false;
        assertThatThrownBy(() -> reconcile("1980-01-02")).hasMessage("EXPIRED_VALUES");
    }

    private CdsExportDemographicBirthDateReconciler.Result reconcile(String lexical)
            throws Exception {
        var graph = fixture.withBirthSource(lexical);
        String address = graph.index().nativeParents().values().iterator().next();
        return new CdsExportDemographicBirthDateReconciler()
                .reconcile(
                        27,
                        address,
                        graph.index(),
                        graph.checked(),
                        graph.fields(),
                        graph.current(),
                        graph.witness(),
                        values(),
                        () -> {});
    }

    private CdsExportRetainedSectionWriter.Values values() {
        return new CdsExportRetainedSectionWriter.Values() {
            public void requireActive() throws IOException {
                if (!active) throw new IOException("EXPIRED_VALUES");
            }

            public Reader text(CdsExportRetainedFieldDto field) throws IOException {
                requireActive();
                return new StringReader(
                        field.logicalInline() == null
                                ? field.originalInline()
                                : field.logicalInline());
            }

            public InputStream binary(CdsExportRetainedFieldDto field) {
                throw new AssertionError("No binary date");
            }
        };
    }
}
