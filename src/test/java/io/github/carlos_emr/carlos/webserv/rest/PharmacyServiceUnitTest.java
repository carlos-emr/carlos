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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.ArrayList;

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

import io.github.carlos_emr.carlos.commn.dao.PharmacyInfoDao;
import io.github.carlos_emr.carlos.commn.exception.AccessDeniedException;
import io.github.carlos_emr.carlos.commn.model.PharmacyInfo;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.to.OscarSearchResponse;
import io.github.carlos_emr.carlos.webserv.rest.to.model.PharmacyInfoTo1;

/**
 * Unit tests for {@link PharmacyService}.
 *
 * <p>Verifies the {@code _rx} privilege checks added to the pharmacy CRUD
 * JAX-RS endpoints (issue #2798): read endpoints require {@code _rx} "r" and
 * the create/update/delete mutators require {@code _rx} "w". Uses a testable
 * subclass that overrides {@code getLoggedInInfo()} to bypass the CXF HTTP
 * request context, with dependencies injected via reflection.</p>
 *
 * @since 2026-06-29
 * @see PharmacyService
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PharmacyService Unit Tests")
@Tag("unit")
@Tag("fast")
class PharmacyServiceUnitTest extends CarlosUnitTestBase {

    private static final String SECURITY_OBJECT = "_rx";

    @Mock
    private PharmacyInfoDao mockPharmacyInfoDao;

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;

    private PharmacyService service;

    @BeforeEach
    void setUp() throws Exception {
        LoggedInInfo loggedInInfo = new LoggedInInfo();
        loggedInInfo.setIp("127.0.0.1");

        service = new PharmacyService() {
            @Override
            protected LoggedInInfo getLoggedInInfo() {
                return loggedInInfo;
            }
        };

        inject("pharmacyInfoDao", mockPharmacyInfoDao);
        inject("securityInfoManager", mockSecurityInfoManager);
    }

    private void inject(String fieldName, Object value) throws Exception {
        Field field = PharmacyService.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(service, value);
    }

    /** Grants/denies whichever privilege ("r" or "w") the endpoint checks. */
    private void grant(boolean allowed) {
        when(mockSecurityInfoManager.hasPrivilege(any(), eq(SECURITY_OBJECT), anyString(), nullable(String.class)))
                .thenReturn(allowed);
    }

    @Nested
    @DisplayName("Read endpoints require _rx read")
    @Tag("read")
    class ReadEndpoints {

        @Test
        @DisplayName("should return pharmacies when caller has _rx read privilege")
        void shouldReturnPharmacies_whenAuthorized() {
            grant(true);
            when(mockPharmacyInfoDao.findAll(0, 10)).thenReturn(new ArrayList<PharmacyInfo>());

            OscarSearchResponse<PharmacyInfoTo1> result = service.getPharmacies(0, 10);

            assertThat(result).isNotNull();
            assertThat(result.getContent()).isEmpty();
        }

        @Test
        @DisplayName("should throw AccessDeniedException for getPharmacies when unauthorized")
        void shouldThrowAccessDenied_forGetPharmaciesWhenUnauthorized() {
            grant(false);

            assertThatThrownBy(() -> service.getPharmacies(0, 10))
                    .isInstanceOf(AccessDeniedException.class);

            verifyNoInteractions(mockPharmacyInfoDao);
        }

        @Test
        @DisplayName("should throw AccessDeniedException for getPharmacy when unauthorized")
        void shouldThrowAccessDenied_forGetPharmacyWhenUnauthorized() {
            grant(false);

            assertThatThrownBy(() -> service.getPharmacy(1))
                    .isInstanceOf(AccessDeniedException.class);

            verifyNoInteractions(mockPharmacyInfoDao);
        }
    }

    @Nested
    @DisplayName("Mutator endpoints require _rx write")
    class MutatorEndpoints {

        @Test
        @DisplayName("should throw AccessDeniedException for addPharmacy when unauthorized")
        @Tag("create")
        void shouldThrowAccessDenied_forAddPharmacyWhenUnauthorized() {
            grant(false);

            assertThatThrownBy(() -> service.addPharmacy(new PharmacyInfoTo1()))
                    .isInstanceOf(AccessDeniedException.class);

            verifyNoInteractions(mockPharmacyInfoDao);
        }

        @Test
        @DisplayName("should throw AccessDeniedException for updatePharmacy when unauthorized")
        @Tag("update")
        void shouldThrowAccessDenied_forUpdatePharmacyWhenUnauthorized() {
            grant(false);

            assertThatThrownBy(() -> service.updatePharmacy(new PharmacyInfoTo1()))
                    .isInstanceOf(AccessDeniedException.class);

            verifyNoInteractions(mockPharmacyInfoDao);
        }

        @Test
        @DisplayName("should throw AccessDeniedException for removePharmacy when unauthorized")
        @Tag("delete")
        void shouldThrowAccessDenied_forRemovePharmacyWhenUnauthorized() {
            grant(false);

            assertThatThrownBy(() -> service.removePharmacy(1))
                    .isInstanceOf(AccessDeniedException.class);

            verifyNoInteractions(mockPharmacyInfoDao);
        }
    }
}
