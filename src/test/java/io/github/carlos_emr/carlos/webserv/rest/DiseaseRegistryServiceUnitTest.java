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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;

import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.commn.dao.DxresearchDAO;
import io.github.carlos_emr.carlos.commn.exception.AccessDeniedException;
import io.github.carlos_emr.carlos.managers.SecurityInfoManagerImpl;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.to.model.IssueTo1;

/**
 * Unit tests for {@link DiseaseRegistryService}.
 *
 * <p>Verifies the patient-level {@code _dxresearch} privilege checks added to the
 * disease-registry endpoints (issue #2280): a {@code w} (write) check on the
 * mutating {@code /{demographicNo}/add} endpoint and an {@code r} (read) check on
 * {@code /getDiseaseRegistry}. Without these, any authenticated user could read or
 * write a patient's diagnosis codes by supplying an arbitrary {@code demographicNo}.
 */
@Tag("unit")
@Tag("rest")
@DisplayName("DiseaseRegistryService unit tests")
class DiseaseRegistryServiceUnitTest {

    private DxresearchDAO dxresearchDao;
    private DiseaseRegistryService service;

    @BeforeEach
    void setUp() {
        dxresearchDao = mock(DxresearchDAO.class);
        when(dxresearchDao.getByDemographicNo(1)).thenReturn(Collections.emptyList());
        service = new TestableDiseaseRegistryService(dxresearchDao);
    }

    @Test
    @DisplayName("should return the disease registry when caller is authorized for the demographic")
    void shouldReturnDiseaseRegistry_whenAuthorized() {
        Response response = service.getDiseaseRegistry(1);

        assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    }

    @Test
    @DisplayName("should deny reading the disease registry for an unauthorized demographic")
    void shouldDenyDiseaseRegistry_forUnauthorizedDemographic() {
        assertThatThrownBy(() -> service.getDiseaseRegistry(6))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("should deny adding to the disease registry for an unauthorized demographic without persisting")
    void shouldDenyAddToDiseaseRegistry_forUnauthorizedDemographic() {
        IssueTo1 issue = new IssueTo1();
        issue.setType("icd9");
        issue.setCode("250");

        assertThatThrownBy(() -> service.addToDiseaseRegistry(6, issue))
                .isInstanceOf(AccessDeniedException.class);

        verify(dxresearchDao, never()).activeEntryExists(6, "icd9", "250");
        verify(dxresearchDao, never()).persist(org.mockito.ArgumentMatchers.any());
    }

    /** Mock SecurityInfoManager that grants access only for demographicNo &lt; 5. */
    static class TestMockSecurityInfoManager extends SecurityInfoManagerImpl {
        @Override
        public boolean hasPrivilege(LoggedInInfo loggedInInfo, String objectName, String privilege, int demographicNo) {
            return demographicNo < 5;
        }
    }

    static class TestableDiseaseRegistryService extends DiseaseRegistryService {
        TestableDiseaseRegistryService(DxresearchDAO dxresearchDao) {
            super();
            this.dxresearchDao = dxresearchDao;
            this.securityInfoManager = new TestMockSecurityInfoManager();
        }

        @Override
        protected LoggedInInfo getLoggedInInfo() {
            return null;
        }
    }
}
