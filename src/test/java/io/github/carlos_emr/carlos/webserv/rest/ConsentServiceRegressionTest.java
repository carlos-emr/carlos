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
package io.github.carlos_emr.carlos.webserv.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.commn.model.ConsentType;
import io.github.carlos_emr.carlos.managers.PatientConsentManager;
import io.github.carlos_emr.carlos.webserv.rest.to.model.ConsentTypeTo1;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Regression tests for consent REST service WebApplicationException handling.
 *
 * @since 2026-05-07
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ConsentService REST exception regression tests")
@Tag("unit")
@Tag("rest")
@Tag("regression")
class ConsentServiceRegressionTest {

    private static final String TEST_PROVIDER_NUMBER = "999998";
    private static final String TEST_CONSENT_NAME = "Email";
    private static final String TEST_CONSENT_DESCRIPTION = "Email consent";
    private static final String TEST_CONSENT_TYPE = "communication";

    @Mock
    private PatientConsentManager patientConsentManager;

    private ConsentService service;

    @BeforeEach
    void setUp() {
        service = new ConsentService();
        service.patientConsentManager = patientConsentManager;
    }

    @Test
    @DisplayName("should return consent for valid ID")
    void shouldReturnConsent_forValidId() {
        when(patientConsentManager.getConsentTypeByConsentTypeId(7)).thenReturn(consentType(7));

        ConsentTypeTo1 response = service.getConsentType(7);

        assertThat(response.getId()).isEqualTo(7);
        assertThat(response.getName()).isEqualTo(TEST_CONSENT_NAME);
        assertThat(response.isActive()).isTrue();
    }

    @Test
    @DisplayName("should return 404 for missing consent")
    void shouldReturn404_forMissingConsent() {
        when(patientConsentManager.getConsentTypeByConsentTypeId(8)).thenReturn(null);

        assertThatThrownBy(() -> service.getConsentType(8))
                .isInstanceOf(WebApplicationException.class)
                .extracting(exception -> ((WebApplicationException) exception).getResponse().getStatus())
                .isEqualTo(Status.NOT_FOUND.getStatusCode());
    }

    @Test
    @DisplayName("should return 500 for internal error")
    void shouldReturn500_forInternalError() {
        when(patientConsentManager.getActiveConsentTypes()).thenThrow(new IllegalStateException("database unavailable"));

        assertThatThrownBy(() -> service.getActiveConsentTypes())
                .isInstanceOf(WebApplicationException.class)
                .extracting(exception -> ((WebApplicationException) exception).getResponse().getStatus())
                .isEqualTo(Status.INTERNAL_SERVER_ERROR.getStatusCode());
    }

    private static ConsentType consentType(int id) {
        ConsentType consentType = new ConsentType();
        consentType.setId(id);
        consentType.setName(TEST_CONSENT_NAME);
        consentType.setDescription(TEST_CONSENT_DESCRIPTION);
        consentType.setType(TEST_CONSENT_TYPE);
        consentType.setProviderNo(TEST_PROVIDER_NUMBER);
        consentType.setActive(true);
        consentType.setRemoteEnabled(Boolean.TRUE);
        return consentType;
    }
}
