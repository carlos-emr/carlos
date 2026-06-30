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

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.to.model.DemographicTo1;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link DemographicService}.
 *
 * These tests assert only the access-control contract enforced at the REST
 * layer, namely that each endpoint:
 * - returns 401 UNAUTHORIZED when the session is absent, and
 * - returns 403 FORBIDDEN when the required privilege is denied, asking
 *   {@link SecurityInfoManager} for the exact privilege string and access
 *   scope expected for that endpoint.
 *
 * They deliberately do NOT assert the role hierarchy (e.g. whether {@code w}
 * implies {@code r}). That hierarchy is owned and enforced by
 * {@code SecurityInfoManagerImpl}; here the manager is mocked, so the only
 * thing worth verifying is the literal privilege string and scope this layer
 * requests for each route.
 *
 * Privilege requested at the REST layer (object-level = no demographicNo,
 * record-level = patient-specific demographicNo):
 * - GET    /demographics         - r, object-level
 * - GET    /demographics/{id}    - r, record-level
 * - POST   /demographics         - w, object-level
 * - PUT    /demographics         - u, record-level
 * - DELETE /demographics/{id}    - w, record-level
 *
 * @since 2026-06-29
 * @see DemographicService
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DemographicService unit tests")
@Tag("unit")
@Tag("fast")
@Tag("security")
@Tag("demographic")
class DemographicServiceUnitTest extends CarlosUnitTestBase {

    @Mock
    private SecurityInfoManager securityInfoManager;

    private DemographicService service;
    /** Swapped to null in UNAUTHORIZED tests. */
    private LoggedInInfo loggedInInfo;

    @BeforeEach
    void setUp() {
        loggedInInfo = new LoggedInInfo();
        service = new DemographicService() {
            @Override
            protected LoggedInInfo getLoggedInInfo() {
                return loggedInInfo;
            }
        };
        injectDependency(service, "securityInfoManager", securityInfoManager);
    }

    // The mock returns false for every hasPrivilege call by default, so the
    // required privilege is implicitly denied; no per-privilege stubbing is
    // needed for the FORBIDDEN paths. Tests instead assert which privilege
    // string and scope the REST layer asked for, via Mockito verify(...).

    private static int statusOf(Throwable ex) {
        return ((WebApplicationException) ex).getResponse().getStatus();
    }

    private void assertThrowsWithStatus(ThrowingRunnable call, Response.Status expected) {
        assertThatThrownBy(call::run)
                .isInstanceOf(WebApplicationException.class)
                .satisfies(ex -> assertThat(statusOf(ex)).isEqualTo(expected.getStatusCode()));
    }

    /** Asserts the object-level privilege string requested (no demographicNo). */
    private void verifyObjectPrivilegeRequested(String privilege) {
        verify(securityInfoManager).hasPrivilege(any(), eq("_demographic"), eq(privilege), isNull());
    }

    /** Asserts the record-level privilege string and demographicNo requested. */
    private void verifyRecordPrivilegeRequested(String privilege, Integer demographicNo) {
        verify(securityInfoManager).hasPrivilege(any(), eq("_demographic"), eq(privilege), eq(demographicNo));
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    @Nested
    @DisplayName("Read privilege (r)")
    class ReadPrivilege {

        @Test
        @DisplayName("should throw 401 when not authenticated for GET /demographics")
        void shouldThrowUnauthorized_whenNotAuthenticated_forGetAllDemographics() {
            loggedInInfo = null;
            assertThrowsWithStatus(() -> service.getAllDemographics(null, null), Response.Status.UNAUTHORIZED);
        }

        @Test
        @DisplayName("should request object-level 'r' and throw 403 when denied for GET /demographics")
        void shouldThrowForbidden_whenReadPrivilegeDenied_forGetAllDemographics() {
            assertThrowsWithStatus(() -> service.getAllDemographics(null, null), Response.Status.FORBIDDEN);
            verifyObjectPrivilegeRequested("r");
        }

        @Test
        @DisplayName("should throw 401 when not authenticated for GET /demographics/{id}")
        void shouldThrowUnauthorized_whenNotAuthenticated_forGetDemographicData() {
            loggedInInfo = null;
            assertThrowsWithStatus(() -> service.getDemographicData(42, Collections.emptyList()), Response.Status.UNAUTHORIZED);
        }

        @Test
        @DisplayName("should request record-level 'r' for the demographicNo and throw 403 when denied for GET /demographics/{id}")
        void shouldThrowForbidden_whenReadPrivilegeDenied_forGetDemographicData() {
            assertThrowsWithStatus(() -> service.getDemographicData(42, Collections.emptyList()), Response.Status.FORBIDDEN);
            verifyRecordPrivilegeRequested("r", 42);
        }
    }

    @Nested
    @DisplayName("Update privilege (u) -- PUT /demographics")
    class UpdatePrivilege {

        private DemographicTo1 validPayload() {
            DemographicTo1 data = new DemographicTo1();
            data.setDemographicNo(42);
            return data;
        }

        @Test
        @DisplayName("should throw 401 when not authenticated for PUT /demographics")
        void shouldThrowUnauthorized_whenNotAuthenticated_forUpdate() {
            loggedInInfo = null;
            assertThrowsWithStatus(() -> service.updateDemographicData(validPayload()), Response.Status.UNAUTHORIZED);
        }

        @Test
        @DisplayName("should request record-level 'u' for the demographicNo and throw 403 when denied for PUT /demographics")
        void shouldThrowForbidden_whenUpdatePrivilegeDenied_forUpdate() {
            assertThrowsWithStatus(() -> service.updateDemographicData(validPayload()), Response.Status.FORBIDDEN);
            verifyRecordPrivilegeRequested("u", 42);
        }

        @Test
        @DisplayName("should throw 401 before 400 when not authenticated and body is null for PUT /demographics")
        void shouldThrowUnauthorized_whenNotAuthenticated_andBodyIsNull_forUpdate() {
            // auth must fire before the null-body check, even in the missing-demographicNo branch
            loggedInInfo = null;
            assertThrowsWithStatus(() -> service.updateDemographicData(null), Response.Status.UNAUTHORIZED);
        }
    }

    @Nested
    @DisplayName("Write privilege (w) -- POST /demographics and DELETE /demographics/{id}")
    class WritePrivilege {

        @Test
        @DisplayName("should throw 401 when not authenticated for POST /demographics")
        void shouldThrowUnauthorized_whenNotAuthenticated_forCreate() {
            loggedInInfo = null;
            assertThrowsWithStatus(() -> service.createDemographicData(new DemographicTo1()), Response.Status.UNAUTHORIZED);
        }

        @Test
        @DisplayName("should request object-level 'w' and throw 403 when denied for POST /demographics")
        void shouldThrowForbidden_whenWritePrivilegeDenied_forCreate() {
            assertThrowsWithStatus(() -> service.createDemographicData(new DemographicTo1()), Response.Status.FORBIDDEN);
            verifyObjectPrivilegeRequested("w");
        }

        @Test
        @DisplayName("should throw 401 when not authenticated for DELETE /demographics/{id}")
        void shouldThrowUnauthorized_whenNotAuthenticated_forDelete() {
            loggedInInfo = null;
            assertThrowsWithStatus(() -> service.deleteDemographicData(42), Response.Status.UNAUTHORIZED);
        }

        @Test
        @DisplayName("should request record-level 'w' for the demographicNo and throw 403 when denied for DELETE /demographics/{id}")
        void shouldThrowForbidden_whenWritePrivilegeDenied_forDelete() {
            assertThrowsWithStatus(() -> service.deleteDemographicData(42), Response.Status.FORBIDDEN);
            verifyRecordPrivilegeRequested("w", 42);
        }
    }
}
