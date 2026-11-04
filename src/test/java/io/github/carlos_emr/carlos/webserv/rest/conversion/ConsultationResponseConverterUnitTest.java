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
package io.github.carlos_emr.carlos.webserv.rest.conversion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.carlos_emr.carlos.commn.model.ConsultationResponse;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.webserv.rest.to.model.ConsultationResponseTo1;
import io.github.carlos_emr.carlos.webserv.rest.to.model.DemographicTo1;
import io.github.carlos_emr.carlos.webserv.rest.to.model.ProfessionalSpecialistTo1;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Converter-level ownership rules for REST consultation responses (issue #4045): the stored
 * response's patient is validated before any submitted field is copied onto the managed entity.
 *
 * @since 2026-09-30
 */
@Tag("unit")
@DisplayName("ConsultationResponseConverter patient ownership")
class ConsultationResponseConverterUnitTest extends CarlosUnitTestBase {

    private static final int PATIENT = 123;

    private final ConsultationResponseConverter converter = new ConsultationResponseConverter();

    @Test
    @DisplayName("should keep the stored patient when the demographic object is omitted")
    void shouldKeepStoredPatient_whenDemographicOmitted() throws Exception {
        ConsultationResponse stored = stored(PATIENT);

        ConsultationResponse result = converter.getAsDomainObject(null, submission(null), stored);

        assertThat(result).isSameAs(stored);
        assertThat(result.getDemographicNo()).isEqualTo(PATIENT);
        assertThat(result.getPlan()).isEqualTo("updated plan");
        assertThat(result.getReferringDocId()).isEqualTo(7);
    }

    @Test
    @DisplayName("should accept a submitted patient that matches the stored response")
    void shouldApplyFields_whenSubmittedPatientMatches() throws Exception {
        ConsultationResponse result = converter.getAsDomainObject(null, submission(PATIENT), stored(PATIENT));

        assertThat(result.getDemographicNo()).isEqualTo(PATIENT);
        assertThat(result.getPlan()).isEqualTo("updated plan");
    }

    @ParameterizedTest
    @ValueSource(ints = {124, 0, -5})
    @DisplayName("should reject a changed or invalid patient before mutating the stored response")
    void shouldRejectPatientChange_beforeMutation(int patient) {
        ConsultationResponse stored = stored(PATIENT);

        assertThatThrownBy(() -> converter.getAsDomainObject(null, submission(patient), stored))
                .isInstanceOf(IllegalArgumentException.class);

        assertUnchanged(stored);
    }

    @Test
    @DisplayName("should reject a demographic object without a patient number before mutation")
    void shouldRejectDemographicWithoutNumber_beforeMutation() {
        ConsultationResponse stored = stored(PATIENT);
        ConsultationResponseTo1 submitted = submission(null);
        submitted.setDemographic(new DemographicTo1());

        assertThatThrownBy(() -> converter.getAsDomainObject(null, submitted, stored))
                .isInstanceOf(IllegalArgumentException.class);

        assertUnchanged(stored);
    }

    @Test
    @DisplayName("should reject a missing referring doctor before mutation instead of dereferencing it")
    void shouldRejectMissingReferringDoctor_beforeMutation() {
        ConsultationResponse stored = stored(PATIENT);
        ConsultationResponseTo1 submitted = submission(PATIENT);
        submitted.setReferringDoctor(null);

        assertThatThrownBy(() -> converter.getAsDomainObject(null, submitted, stored))
                .isInstanceOf(IllegalArgumentException.class);

        assertUnchanged(stored);
    }

    @Test
    @DisplayName("should reject a stored response that has no patient")
    void shouldRejectStoredResponse_withoutPatient() {
        ConsultationResponse stored = stored(null);

        assertThatThrownBy(() -> converter.getAsDomainObject(null, submission(null), stored))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(stored.getPlan()).isEqualTo("original plan");
    }

    @Test
    @DisplayName("should reject missing submission or stored record instead of throwing a null pointer")
    void shouldRejectMissingRecords_withValidationFailure() {
        assertThatThrownBy(() -> converter.getAsDomainObject(null, null, stored(PATIENT)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> converter.getAsDomainObject(null, submission(PATIENT), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("should require a patient for a new response")
    void shouldRequirePatient_forNewResponse() throws Exception {
        assertThatThrownBy(() -> converter.getAsDomainObject(null, submission(null)))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(converter.getAsDomainObject(null, submission(PATIENT)).getDemographicNo()).isEqualTo(PATIENT);
    }

    private static void assertUnchanged(ConsultationResponse stored) {
        assertThat(stored.getDemographicNo()).isEqualTo(PATIENT);
        assertThat(stored.getPlan()).isEqualTo("original plan");
        assertThat(stored.getReferringDocId()).isEqualTo(3);
    }

    private static ConsultationResponse stored(Integer patient) {
        ConsultationResponse response = new ConsultationResponse();
        ReflectionTestUtils.setField(response, "id", 456);
        response.setDemographicNo(patient);
        response.setPlan("original plan");
        response.setReferringDocId(3);
        return response;
    }

    private static ConsultationResponseTo1 submission(Integer patient) {
        ConsultationResponseTo1 response = new ConsultationResponseTo1();
        response.setId(456);
        if (patient != null) {
            DemographicTo1 demographic = new DemographicTo1();
            demographic.setDemographicNo(patient);
            response.setDemographic(demographic);
        }
        ProfessionalSpecialistTo1 referring = new ProfessionalSpecialistTo1();
        referring.setId(7);
        response.setReferringDoctor(referring);
        response.setPlan("updated plan");
        return response;
    }
}
