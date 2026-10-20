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

import io.github.carlos_emr.carlos.commn.model.ConsultationRequest;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.webserv.rest.to.model.ConsultationRequestTo1;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Converter-level ownership rules for REST consultation requests (issue #4045): the stored
 * request's patient is validated before any submitted field is copied onto the managed entity.
 *
 * @since 2026-09-30
 */
@Tag("unit")
@DisplayName("ConsultationRequestConverter patient ownership")
class ConsultationRequestConverterUnitTest extends CarlosUnitTestBase {

    private static final int PATIENT = 123;

    private final ConsultationRequestConverter converter = new ConsultationRequestConverter();

    @Test
    @DisplayName("should apply submitted fields when the patient matches the stored request")
    void shouldApplyFields_whenPatientMatches() throws Exception {
        ConsultationRequest stored = stored();

        ConsultationRequest result = converter.getAsDomainObject(null, submission(PATIENT), stored);

        assertThat(result).isSameAs(stored);
        assertThat(result.getDemographicId()).isEqualTo(PATIENT);
        assertThat(result.getReasonForReferral()).isEqualTo("updated reason");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {124, 0, -1})
    @DisplayName("should reject a changed, missing or invalid patient before mutating the stored request")
    void shouldRejectPatientChange_beforeMutation(Integer patient) {
        ConsultationRequest stored = stored();

        assertThatThrownBy(() -> converter.getAsDomainObject(null, submission(patient), stored))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(stored.getDemographicId()).isEqualTo(PATIENT);
        assertThat(stored.getReasonForReferral()).isEqualTo("original reason");
        assertThat(stored.getStatus()).isEqualTo("4");
    }

    @Test
    @DisplayName("should reject missing submission or stored record instead of throwing a null pointer")
    void shouldRejectMissingRecords_withValidationFailure() {
        assertThatThrownBy(() -> converter.getAsDomainObject(null, null, stored()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> converter.getAsDomainObject(null, submission(PATIENT), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("should require a valid patient for a new request")
    void shouldRequirePatient_forNewRequest() throws Exception {
        assertThatThrownBy(() -> converter.getAsDomainObject(null, submission(null)))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(converter.getAsDomainObject(null, submission(PATIENT)).getDemographicId()).isEqualTo(PATIENT);
    }

    private static ConsultationRequest stored() {
        ConsultationRequest request = new ConsultationRequest();
        ReflectionTestUtils.setField(request, "id", 456);
        request.setDemographicId(PATIENT);
        request.setReasonForReferral("original reason");
        request.setStatus("4");
        return request;
    }

    private static ConsultationRequestTo1 submission(Integer patient) {
        ConsultationRequestTo1 request = new ConsultationRequestTo1();
        request.setId(456);
        request.setDemographicId(patient);
        request.setReasonForReferral("updated reason");
        return request;
    }
}
