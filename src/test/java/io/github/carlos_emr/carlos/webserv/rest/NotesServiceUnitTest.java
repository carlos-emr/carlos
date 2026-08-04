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

import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNoteExt;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.to.model.NoteExtTo1;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link NotesService} privilege enforcement.
 *
 * <p>Verifies that {@code getGroupNoteExt} enforces the {@code _eChart} security
 * object before returning clinical note extension data (treatment, problem
 * description, exposure details, etc.). See #2798.</p>
 *
 * @see NotesService
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("NotesService Unit Tests")
@Tag("unit")
@Tag("fast")
class NotesServiceUnitTest extends CarlosUnitTestBase {

    private static final String ECHART = "_eChart";

    @Mock
    private CaseManagementManager mockCaseManagementMgr;

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;

    private NotesService service;

    @BeforeEach
    void setUp() throws Exception {
        Provider provider = mock(Provider.class);
        when(provider.getProviderNo()).thenReturn("101");
        LoggedInInfo loggedInInfo = new LoggedInInfo();
        loggedInInfo.setLoggedInProvider(provider);
        loggedInInfo.setIp("127.0.0.1");

        final LoggedInInfo capturedInfo = loggedInInfo;
        service = new NotesService() {
            @Override
            protected LoggedInInfo getLoggedInInfo() {
                return capturedInfo;
            }
        };

        inject("caseManagementMgr", mockCaseManagementMgr);
        inject("securityInfoManager", mockSecurityInfoManager);
    }

    private void inject(String fieldName, Object value) throws Exception {
        Field field = NotesService.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(service, value);
    }

    @Test
    @DisplayName("should return note extension data when caller has eChart read privilege")
    @Tag("read")
    void shouldReturnGroupNoteExt_whenCallerHasReadPrivilege() {
        when(mockSecurityInfoManager.hasPrivilege(any(), eq(ECHART), eq("r"), any())).thenReturn(true);
        CaseManagementNoteExt ext = new CaseManagementNoteExt();
        ext.setKeyVal(CaseManagementNoteExt.TREATMENT);
        ext.setValue("Sertraline 50mg daily");
        when(mockCaseManagementMgr.getExtByNote(94L)).thenReturn(Arrays.asList(ext));

        NoteExtTo1 result = service.getGroupNoteExt(94L);

        assertThat(result.getNoteId()).isEqualTo(94L);
        assertThat(result.getTreatment()).isEqualTo("Sertraline 50mg daily");
        verify(mockCaseManagementMgr).getExtByNote(94L);
    }

    @Test
    @DisplayName("should deny note extension data when caller lacks eChart read privilege")
    @Tag("read")
    void shouldDenyGroupNoteExt_whenCallerLacksReadPrivilege() {
        when(mockSecurityInfoManager.hasPrivilege(any(), anyString(), anyString(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.getGroupNoteExt(94L))
            .isInstanceOf(SecurityException.class)
            .hasMessage("missing required sec object (_eChart)");

        verify(mockCaseManagementMgr, never()).getExtByNote(anyLong());
    }
}
