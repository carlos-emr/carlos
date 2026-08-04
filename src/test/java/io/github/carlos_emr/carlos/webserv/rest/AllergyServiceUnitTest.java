/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.webserv.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;

import jakarta.ws.rs.BadRequestException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.commn.exception.AccessDeniedException;
import io.github.carlos_emr.carlos.managers.AllergyManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManagerImpl;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.to.AllergyResponse;

/**
 * Unit tests for {@link AllergyService}.
 *
 * <p>Verifies the patient-level {@code _allergy} read privilege check added to
 * the {@code /allergies/active} endpoint so an authenticated user without access
 * to a patient cannot read that patient's active allergies by supplying an
 * arbitrary {@code demographicNo} (issue #2280).
 */
@Tag("unit")
@Tag("rest")
@DisplayName("AllergyService unit tests")
class AllergyServiceUnitTest {

    private AllergyManager allergyManager;
    private AllergyService service;

    @BeforeEach
    void setUp() {
        allergyManager = mock(AllergyManager.class);
        when(allergyManager.getActiveAllergies(null, 1)).thenReturn(Collections.emptyList());
        service = new TestableAllergyService(allergyManager);
    }

    @Test
    @DisplayName("should return active allergies when caller is authorized for the demographic")
    void shouldReturnActiveAllergies_whenAuthorized() {
        AllergyResponse response = service.getCurrentAllergies(1);

        assertThat(response).isNotNull();
        assertThat(response.getAllergies()).isEmpty();
    }

    @Test
    @DisplayName("should deny access to active allergies for an unauthorized demographic")
    void shouldDenyActiveAllergies_forUnauthorizedDemographic() {
        assertThatThrownBy(() -> service.getCurrentAllergies(6))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("should reject the request when demographicNo is missing")
    void shouldRejectActiveAllergies_whenDemographicNoMissing() {
        assertThatThrownBy(() -> service.getCurrentAllergies(null))
                .isInstanceOf(BadRequestException.class);
    }

    /** Mock SecurityInfoManager that grants access only for demographicNo &lt; 5. */
    static class TestMockSecurityInfoManager extends SecurityInfoManagerImpl {
        @Override
        public boolean hasPrivilege(LoggedInInfo loggedInInfo, String objectName, String privilege, int demographicNo) {
            return demographicNo < 5;
        }
    }

    static class TestableAllergyService extends AllergyService {
        TestableAllergyService(AllergyManager allergyManager) {
            super();
            this.allergyManager = allergyManager;
            this.securityInfoManager = new TestMockSecurityInfoManager();
        }

        @Override
        protected LoggedInInfo getLoggedInInfo() {
            return null;
        }
    }
}
