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
package io.github.carlos_emr.carlos.casemgmt.service;

import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import java.util.Collections;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies that a patient without program admissions receives no program rights.
 *
 * @since 2026-10-24
 */
@Tag("unit")
class CaseManagementProgramAccessUnitTest {
    @Test
    void shouldReturnNoProgramRights_whenPatientHasNoProgramAdmissions() {
        DemographicDao demographics = mock(DemographicDao.class);
        when(demographics.getProgramIdByDemoNo(123)).thenReturn(Collections.emptyList());
        CaseManagementManagerImpl manager = new CaseManagementManagerImpl();
        manager.setDemographicDao(demographics);

        assertTrue(manager.getAccessRight("999998", "123", null).isEmpty());
        assertFalse(manager.hasAccessRight("note-read-only", "access", "999998", "123", "0"));
    }
}
