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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.commn.exception.AccessDeniedException;
import io.github.carlos_emr.carlos.managers.LabManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManagerImpl;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.to.LabResponse;

/**
 * Unit tests for {@link LabService}.
 *
 * <p>Verifies the patient-level {@code _lab} read privilege check added to the
 * {@code /labs/hl7LabsByDemographicNo} endpoint so an authenticated user without
 * access to a patient cannot read that patient's HL7 lab messages by supplying
 * an arbitrary {@code demographicNo} (issue #2280).
 */
@Tag("unit")
@Tag("rest")
@DisplayName("LabService unit tests")
class LabServiceUnitTest {

    private LabManager labManager;
    private LabService service;

    @BeforeEach
    void setUp() {
        labManager = mock(LabManager.class);
        when(labManager.getHl7Messages(null, 1, 0, 10)).thenReturn(Collections.emptyList());
        service = new TestableLabService(labManager);
    }

    @Test
    @DisplayName("should return HL7 lab messages when caller is authorized for the demographic")
    void shouldReturnHl7Labs_whenAuthorized() {
        LabResponse response = service.getHl7LabsByDemographicNo(1, 0, 10);

        assertThat(response).isNotNull();
        assertThat(response.getMessages()).isEmpty();
    }

    @Test
    @DisplayName("should deny access to HL7 lab messages for an unauthorized demographic")
    void shouldDenyHl7Labs_forUnauthorizedDemographic() {
        assertThatThrownBy(() -> service.getHl7LabsByDemographicNo(6, 0, 10))
                .isInstanceOf(AccessDeniedException.class);
    }

    /** Mock SecurityInfoManager that grants access only for demographicNo &lt; 5. */
    static class TestMockSecurityInfoManager extends SecurityInfoManagerImpl {
        @Override
        public boolean hasPrivilege(LoggedInInfo loggedInInfo, String objectName, String privilege, int demographicNo) {
            return demographicNo < 5;
        }
    }

    static class TestableLabService extends LabService {
        TestableLabService(LabManager labManager) {
            super();
            this.labManager = labManager;
            this.securityInfoManager = new TestMockSecurityInfoManager();
        }

        @Override
        protected LoggedInInfo getLoggedInInfo() {
            return null;
        }
    }
}
