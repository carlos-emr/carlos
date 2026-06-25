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
package io.github.carlos_emr.carlos.managers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import io.github.carlos_emr.carlos.commn.model.DigitalSignature;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@DisplayName("Consultation stamp outcome")
@Tag("unit")
class ConsultationStampOutcomeUnitTest {

    @Test
    @DisplayName("rejects a SAVED outcome with no signature")
    void shouldRejectConstruction_whenSavedWithoutSignature() {
        assertThatThrownBy(() -> new ConsultationStampOutcome(ConsultationStampOutcome.Status.SAVED, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("rejects a failure outcome that carries a signature")
    void shouldRejectConstruction_whenFailureCarriesSignature() {
        assertThatThrownBy(() ->
                new ConsultationStampOutcome(ConsultationStampOutcome.Status.ERROR, mock(DigitalSignature.class)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("reports saved with its signature for a SAVED outcome")
    void shouldReportSaved_forSavedOutcome() {
        DigitalSignature signature = mock(DigitalSignature.class);
        ConsultationStampOutcome outcome = new ConsultationStampOutcome(ConsultationStampOutcome.Status.SAVED, signature);

        assertThat(outcome.isSaved()).isTrue();
        assertThat(outcome.isGenuineFailure()).isFalse();
        assertThat(outcome.signature()).isSameAs(signature);
    }

    @Test
    @DisplayName("classifies not-permitted, stamp-file-missing and error as genuine failures")
    void shouldReportGenuineFailure_forFailureStatuses() {
        assertThat(new ConsultationStampOutcome(ConsultationStampOutcome.Status.NOT_PERMITTED, null).isGenuineFailure()).isTrue();
        assertThat(new ConsultationStampOutcome(ConsultationStampOutcome.Status.STAMP_FILE_MISSING, null).isGenuineFailure()).isTrue();
        assertThat(new ConsultationStampOutcome(ConsultationStampOutcome.Status.ERROR, null).isGenuineFailure()).isTrue();
    }

    @Test
    @DisplayName("classifies disabled and no-session as benign, not genuine failures")
    void shouldReportBenign_forDisabledAndNoSessionStatuses() {
        assertThat(new ConsultationStampOutcome(ConsultationStampOutcome.Status.SIGNATURES_DISABLED, null).isGenuineFailure()).isFalse();
        assertThat(new ConsultationStampOutcome(ConsultationStampOutcome.Status.NO_SESSION, null).isGenuineFailure()).isFalse();
    }
}
