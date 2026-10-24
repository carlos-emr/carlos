/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.casemgmt.service;

import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import java.util.Collections;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
