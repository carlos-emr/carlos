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
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.commn.dao.CdsLaboratoryResultDao;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class CdsLaboratoryReadServiceUnitTest {
    @Test
    void shouldRequireSensitivePrivilege_whenAnySourceResultIsBlocked() {
        var security = mock(SecurityInfoManager.class);
        var results = mock(CdsLaboratoryResultDao.class);
        var actor = mock(LoggedInInfo.class);
        when(actor.getLoggedInSecurity()).thenReturn(mock(Security.class));
        when(security.hasPrivilegeReadOnly(actor, "_lab", "r", "42")).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(actor, 42)).thenReturn(true);
        when(results.hasBlockedResult(42)).thenReturn(true);
        var service = new CdsLaboratoryReadService(security, results);

        assertThatThrownBy(() -> service.requireSourceFieldsReadable(actor, 42))
                .isInstanceOf(SecurityException.class);
        when(security.hasPrivilegeReadOnly(actor, "_lab_sensitive", "r", "42")).thenReturn(true);
        assertThatCode(() -> service.requireSourceFieldsReadable(actor, 42)).doesNotThrowAnyException();
    }

    @Test
    void shouldRefuseUnauthenticatedRead_beforeLookingUpSourceRestrictions() {
        var security = mock(SecurityInfoManager.class);
        var results = mock(CdsLaboratoryResultDao.class);
        var service = new CdsLaboratoryReadService(security, results);
        assertThatThrownBy(() -> service.requireSourceFieldsReadable(null, 42))
                .isInstanceOf(SecurityException.class);
        verifyNoInteractions(security, results);
    }
}
