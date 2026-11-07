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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import io.github.carlos_emr.carlos.commn.exception.AccessDeniedException;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.managers.LabManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.to.LabResponse;

/**
 * Unit tests for {@link LabService} privilege enforcement.
 *
 * <p>Verifies the patient-level {@code _lab} read privilege check on
 * {@code /labs/hl7LabsByDemographicNo} (issue #2280): the check must carry the
 * requested {@code demographicNo} so an authenticated user without access to that
 * patient cannot read their HL7 lab messages. This mirrors the {@code _lab} write
 * check the same class already enforces on {@code /hl7LabUpload}.</p>
 *
 * @see LabService
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("LabService Unit Tests")
@Tag("unit")
@Tag("rest")
class LabServiceUnitTest extends CarlosUnitTestBase {

    private static final String LAB = "_lab";

    @Mock
    private LabManager mockLabManager;

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;

    private LabService service;

    @BeforeEach
    void setUp() throws Exception {
        Provider provider = mock(Provider.class);
        when(provider.getProviderNo()).thenReturn("101");
        LoggedInInfo loggedInInfo = new LoggedInInfo();
        loggedInInfo.setLoggedInProvider(provider);
        loggedInInfo.setIp("127.0.0.1");

        final LoggedInInfo capturedInfo = loggedInInfo;
        service = new LabService() {
            @Override
            protected LoggedInInfo getLoggedInInfo() {
                return capturedInfo;
            }
        };

        inject("labManager", mockLabManager);
        inject("securityInfoManager", mockSecurityInfoManager);
    }

    private void inject(String fieldName, Object value) throws Exception {
        Field field = LabService.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(service, value);
    }

    @Test
    @DisplayName("should return HL7 lab messages when caller has lab read privilege for the patient")
    @Tag("read")
    void shouldReturnHl7Labs_whenCallerHasReadPrivilege() {
        when(mockSecurityInfoManager.hasPrivilege(any(), eq(LAB), eq("r"), eq(1))).thenReturn(true);
        when(mockLabManager.getHl7Messages(any(), eq(1), eq(0), eq(10))).thenReturn(Collections.emptyList());

        LabResponse response = service.getHl7LabsByDemographicNo(1, 0, 10);

        assertThat(response).isNotNull();
        assertThat(response.getMessages()).isEmpty();
        verify(mockSecurityInfoManager).hasPrivilege(any(), eq(LAB), eq("r"), eq(1));
    }

    @Test
    @DisplayName("should deny HL7 lab messages when caller lacks lab read privilege for the patient")
    @Tag("read")
    void shouldDenyHl7Labs_whenCallerLacksReadPrivilege() {
        when(mockSecurityInfoManager.hasPrivilege(any(), eq(LAB), eq("r"), eq(6))).thenReturn(false);

        assertThatThrownBy(() -> service.getHl7LabsByDemographicNo(6, 0, 10))
            .isInstanceOf(AccessDeniedException.class);

        verify(mockLabManager, never()).getHl7Messages(any(), anyInt(), anyInt(), anyInt());
    }
}
