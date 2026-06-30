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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;

/**
 * Unit tests for {@link DemographicService}.
 *
 * Covers read (r), update (u), and write (w) privilege tiers and verifies:
 * - 401 UNAUTHORIZED when the session is absent
 * - 403 FORBIDDEN when the required privilege is denied
 * - 403 FORBIDDEN when a different privilege is held but not the required one
 *
 * Privilege mapping enforced at the REST layer:
 * - GET /demographics         - r
 * - GET /demographics/{id}    - r
 * - POST /demographics        - w
 * - PUT /demographics         - u
 * - DELETE /demographics/{id} - w
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

    private void grantPrivilege(String privilege) {
        lenient().when(securityInfoManager.hasPrivilege(any(), eq("_demographic"), eq(privilege), isNull()))
                .thenReturn(true);
        lenient().when(securityInfoManager.hasPrivilege(any(), eq("_demographic"), eq(privilege), anyInt()))
                .thenReturn(true);
    }

    private void denyPrivilege(String privilege) {
        lenient().when(securityInfoManager.hasPrivilege(any(), eq("_demographic"), eq(privilege), isNull()))
                .thenReturn(false);
        lenient().when(securityInfoManager.hasPrivilege(any(), eq("_demographic"), eq(privilege), anyInt()))
                .thenReturn(false);
    }

    private static int statusOf(Throwable ex) {
        return ((WebApplicationException) ex).getResponse().getStatus();
    }

    @Nested
    @DisplayName("Read privilege (r)")
    class ReadPrivilege {

        @Test
        @DisplayName("should throw 401 when not authenticated for GET /demographics")
        void shouldThrowUnauthorized_whenNotAuthenticated_forGetAllDemographics() {
            loggedInInfo = null;
            assertThatThrownBy(() -> service.getAllDemographics(null, null))
                    .isInstanceOf(WebApplicationException.class)
                    .satisfies(ex -> assertThat(statusOf(ex))
                            .isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode()));
        }

        @Test
        @DisplayName("should throw 403 when read privilege denied for GET /demographics")
        void shouldThrowForbidden_whenReadPrivilegeDenied_forGetAllDemographics() {
            denyPrivilege("r");
            assertThatThrownBy(() -> service.getAllDemographics(null, null))
                    .isInstanceOf(WebApplicationException.class)
                    .satisfies(ex -> assertThat(statusOf(ex))
                            .isEqualTo(Response.Status.FORBIDDEN.getStatusCode()));
        }

        @Test
        @DisplayName("should throw 401 when not authenticated for GET /demographics/{id}")
        void shouldThrowUnauthorized_whenNotAuthenticated_forGetDemographicData() {
            loggedInInfo = null;
            assertThatThrownBy(() -> service.getDemographicData(42, Collections.emptyList()))
                    .isInstanceOf(WebApplicationException.class)
                    .satisfies(ex -> assertThat(statusOf(ex))
                            .isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode()));
        }

        @Test
        @DisplayName("should throw 403 when read privilege denied for GET /demographics/{id}")
        void shouldThrowForbidden_whenReadPrivilegeDenied_forGetDemographicData() {
            denyPrivilege("r");
            assertThatThrownBy(() -> service.getDemographicData(42, Collections.emptyList()))
                    .isInstanceOf(WebApplicationException.class)
                    .satisfies(ex -> assertThat(statusOf(ex))
                            .isEqualTo(Response.Status.FORBIDDEN.getStatusCode()));
        }

        @Test
        @DisplayName("should throw 403 when only update privilege held for GET /demographics")
        void shouldThrowForbidden_whenOnlyUpdatePrivilege_forGetAllDemographics() {
            grantPrivilege("u");
            denyPrivilege("r");
            assertThatThrownBy(() -> service.getAllDemographics(null, null))
                    .isInstanceOf(WebApplicationException.class)
                    .satisfies(ex -> assertThat(statusOf(ex))
                            .isEqualTo(Response.Status.FORBIDDEN.getStatusCode()));
        }

        @Test
        @DisplayName("should throw 403 when only write privilege held for GET /demographics")
        void shouldThrowForbidden_whenOnlyWritePrivilege_forGetAllDemographics() {
            grantPrivilege("w");
            denyPrivilege("r");
            assertThatThrownBy(() -> service.getAllDemographics(null, null))
                    .isInstanceOf(WebApplicationException.class)
                    .satisfies(ex -> assertThat(statusOf(ex))
                            .isEqualTo(Response.Status.FORBIDDEN.getStatusCode()));
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
            assertThatThrownBy(() -> service.updateDemographicData(validPayload()))
                    .isInstanceOf(WebApplicationException.class)
                    .satisfies(ex -> assertThat(statusOf(ex))
                            .isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode()));
        }

        @Test
        @DisplayName("should throw 403 when update privilege denied for PUT /demographics")
        void shouldThrowForbidden_whenUpdatePrivilegeDenied_forUpdate() {
            denyPrivilege("u");
            assertThatThrownBy(() -> service.updateDemographicData(validPayload()))
                    .isInstanceOf(WebApplicationException.class)
                    .satisfies(ex -> assertThat(statusOf(ex))
                            .isEqualTo(Response.Status.FORBIDDEN.getStatusCode()));
        }

        @Test
        @DisplayName("should throw 403 when only read privilege held for PUT /demographics")
        void shouldThrowForbidden_whenOnlyReadPrivilege_forUpdate() {
            grantPrivilege("r");
            denyPrivilege("u");
            assertThatThrownBy(() -> service.updateDemographicData(validPayload()))
                    .isInstanceOf(WebApplicationException.class)
                    .satisfies(ex -> assertThat(statusOf(ex))
                            .isEqualTo(Response.Status.FORBIDDEN.getStatusCode()));
        }

        @Test
        @DisplayName("should throw 403 when only write privilege held for PUT /demographics")
        void shouldThrowForbidden_whenOnlyWritePrivilege_forUpdate() {
            // w does not imply u -- PUT /demographics requires u, not w
            grantPrivilege("w");
            denyPrivilege("u");
            assertThatThrownBy(() -> service.updateDemographicData(validPayload()))
                    .isInstanceOf(WebApplicationException.class)
                    .satisfies(ex -> assertThat(statusOf(ex))
                            .isEqualTo(Response.Status.FORBIDDEN.getStatusCode()));
        }

        @Test
        @DisplayName("should throw 401 before 400 when not authenticated and body is null for PUT /demographics")
        void shouldThrowUnauthorized_whenNotAuthenticated_andBodyIsNull_forUpdate() {
            // auth must fire before the null-body check, even in the missing-demographicNo branch
            loggedInInfo = null;
            assertThatThrownBy(() -> service.updateDemographicData(null))
                    .isInstanceOf(WebApplicationException.class)
                    .satisfies(ex -> assertThat(statusOf(ex))
                            .isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode()));
        }
    }

    @Nested
    @DisplayName("Write privilege (w) -- POST /demographics and DELETE /demographics/{id}")
    class WritePrivilege {

        @Test
        @DisplayName("should throw 401 when not authenticated for POST /demographics")
        void shouldThrowUnauthorized_whenNotAuthenticated_forCreate() {
            loggedInInfo = null;
            assertThatThrownBy(() -> service.createDemographicData(new DemographicTo1()))
                    .isInstanceOf(WebApplicationException.class)
                    .satisfies(ex -> assertThat(statusOf(ex))
                            .isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode()));
        }

        @Test
        @DisplayName("should throw 403 when write privilege denied for POST /demographics")
        void shouldThrowForbidden_whenWritePrivilegeDenied_forCreate() {
            denyPrivilege("w");
            assertThatThrownBy(() -> service.createDemographicData(new DemographicTo1()))
                    .isInstanceOf(WebApplicationException.class)
                    .satisfies(ex -> assertThat(statusOf(ex))
                            .isEqualTo(Response.Status.FORBIDDEN.getStatusCode()));
        }

        @Test
        @DisplayName("should throw 403 when only read privilege held for POST /demographics")
        void shouldThrowForbidden_whenOnlyReadPrivilege_forCreate() {
            grantPrivilege("r");
            denyPrivilege("w");
            assertThatThrownBy(() -> service.createDemographicData(new DemographicTo1()))
                    .isInstanceOf(WebApplicationException.class)
                    .satisfies(ex -> assertThat(statusOf(ex))
                            .isEqualTo(Response.Status.FORBIDDEN.getStatusCode()));
        }

        @Test
        @DisplayName("should throw 401 when not authenticated for DELETE /demographics/{id}")
        void shouldThrowUnauthorized_whenNotAuthenticated_forDelete() {
            loggedInInfo = null;
            assertThatThrownBy(() -> service.deleteDemographicData(42))
                    .isInstanceOf(WebApplicationException.class)
                    .satisfies(ex -> assertThat(statusOf(ex))
                            .isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode()));
        }

        @Test
        @DisplayName("should throw 403 when write privilege denied for DELETE /demographics/{id}")
        void shouldThrowForbidden_whenWritePrivilegeDenied_forDelete() {
            denyPrivilege("w");
            assertThatThrownBy(() -> service.deleteDemographicData(42))
                    .isInstanceOf(WebApplicationException.class)
                    .satisfies(ex -> assertThat(statusOf(ex))
                            .isEqualTo(Response.Status.FORBIDDEN.getStatusCode()));
        }

        @Test
        @DisplayName("should throw 403 when only delete privilege held for DELETE /demographics/{id}")
        void shouldThrowForbidden_whenOnlyDeletePrivilege_forDelete() {
            // DELETE /demographics/{id} requires w to align with DemographicManagerImpl.deleteDemographic;
            // holding d alone is not sufficient.
            grantPrivilege("d");
            denyPrivilege("w");
            assertThatThrownBy(() -> service.deleteDemographicData(42))
                    .isInstanceOf(WebApplicationException.class)
                    .satisfies(ex -> assertThat(statusOf(ex))
                            .isEqualTo(Response.Status.FORBIDDEN.getStatusCode()));
        }

        @Test
        @DisplayName("should throw 403 when only read privilege held for DELETE /demographics/{id}")
        void shouldThrowForbidden_whenOnlyReadPrivilege_forDelete() {
            grantPrivilege("r");
            denyPrivilege("w");
            assertThatThrownBy(() -> service.deleteDemographicData(42))
                    .isInstanceOf(WebApplicationException.class)
                    .satisfies(ex -> assertThat(statusOf(ex))
                            .isEqualTo(Response.Status.FORBIDDEN.getStatusCode()));
        }
    }
}
