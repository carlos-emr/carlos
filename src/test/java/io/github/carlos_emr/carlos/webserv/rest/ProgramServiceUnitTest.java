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
 *
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.webserv.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Date;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import io.github.carlos_emr.carlos.PMmodule.model.ProgramProvider;
import io.github.carlos_emr.carlos.commn.model.Admission;
import io.github.carlos_emr.carlos.PMmodule.service.AdmissionManager;
import io.github.carlos_emr.carlos.commn.exception.AccessDeniedException;
import io.github.carlos_emr.carlos.managers.ProgramManager2;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.to.AbstractSearchResponse;
import io.github.carlos_emr.carlos.webserv.rest.to.model.AdmissionTo1;
import io.github.carlos_emr.carlos.webserv.rest.to.model.ProgramTo1;

/**
 * Unit tests for {@link ProgramService}.
 *
 * <p>Verifies the {@code _pmm_management} read privilege check added to the
 * program JAX-RS endpoints (issue #2798). Uses a testable subclass that
 * overrides {@code getLoggedInInfo()} to bypass the CXF HTTP request context,
 * with dependencies injected via reflection.</p>
 *
 * @since 2026-06-29
 * @see ProgramService
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ProgramService Unit Tests")
@Tag("unit")
@Tag("fast")
class ProgramServiceUnitTest extends CarlosUnitTestBase {

    private static final String SECURITY_OBJECT = "_pmm_management";

    @Mock
    private ProgramManager2 mockProgramManager;

    @Mock
    private AdmissionManager mockAdmissionManager;

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;

    private ProgramService service;

    @BeforeEach
    void setUp() throws Exception {
        LoggedInInfo loggedInInfo = new LoggedInInfo();
        loggedInInfo.setIp("127.0.0.1");

        service = new ProgramService() {
            @Override
            protected LoggedInInfo getLoggedInInfo() {
                return loggedInInfo;
            }
        };

        inject("programManager", mockProgramManager);
        inject("admissionManager", mockAdmissionManager);
        inject("securityInfoManager", mockSecurityInfoManager);
    }

    private void inject(String fieldName, Object value) throws Exception {
        Field field = ProgramService.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(service, value);
    }

    private void grant(boolean allowed) {
        when(mockSecurityInfoManager.hasPrivilege(any(), eq(SECURITY_OBJECT), anyString(), nullable(String.class)))
                .thenReturn(allowed);
    }

    @Nested
    @DisplayName("getProgramList")
    @Tag("read")
    class GetProgramList {

        @Test
        @DisplayName("should return program response when caller has _pmm_management read privilege")
        void shouldReturnProgramResponse_whenAuthorized() throws Exception {
            grant(true);
            when(mockProgramManager.getProgramDomain(any(), any())).thenReturn(new ArrayList<ProgramProvider>());

            AbstractSearchResponse<ProgramTo1> response = service.getProgramList();

            assertThat(response).isNotNull();
            assertThat(response.getTotal()).isZero();
        }

        @Test
        @DisplayName("should throw AccessDeniedException when caller lacks _pmm_management privilege")
        void shouldThrowAccessDenied_whenUnauthorized() {
            grant(false);

            assertThatThrownBy(() -> service.getProgramList())
                    .isInstanceOf(AccessDeniedException.class);

            verifyNoInteractions(mockProgramManager);
        }
    }

    @Nested
    @DisplayName("getPatientList")
    @Tag("read")
    class GetPatientList {

        @Test
        @DisplayName("should return admission response when caller has _pmm_management read privilege")
        void shouldReturnAdmissionResponse_whenAuthorized() throws Exception {
            grant(true);
            when(mockAdmissionManager.findAdmissionsByProgramAndDate(any(), any(), any(Date.class), anyInt(), anyInt()))
                    .thenReturn(new ArrayList<Admission>());
            when(mockAdmissionManager.findAdmissionsByProgramAndDateAsCount(any(), any(), any(Date.class)))
                    .thenReturn(0);

            AbstractSearchResponse<AdmissionTo1> response = service.getPatientList("1", null, 0, 10);

            assertThat(response).isNotNull();
            assertThat(response.getContent()).isEmpty();
            assertThat(response.getTotal()).isZero();
        }

        @Test
        @DisplayName("should throw AccessDeniedException when caller lacks _pmm_management privilege")
        void shouldThrowAccessDenied_whenUnauthorized() {
            grant(false);

            assertThatThrownBy(() -> service.getPatientList("1", null, 0, 10))
                    .isInstanceOf(AccessDeniedException.class);

            verifyNoInteractions(mockAdmissionManager);
        }
    }
}
