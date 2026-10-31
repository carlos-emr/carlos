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

import io.github.carlos_emr.carlos.commn.dao.CdsLegacyProjectionDao;
import io.github.carlos_emr.carlos.commn.exception.AccessDeniedException;
import io.github.carlos_emr.carlos.commn.model.Allergy;
import io.github.carlos_emr.carlos.managers.AllergyManager;
import io.github.carlos_emr.carlos.managers.RxManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.AllergyService;
import io.github.carlos_emr.carlos.webserv.rest.RxWebService;
import jakarta.ws.rs.WebApplicationException;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class CdsCategoryProjectionBoundaryUnitTest {
    @Test void shouldAuthorizeRx_beforeProtectedRepresentationQuery() {
        LoggedInInfo info = new LoggedInInfo();
        CdsLegacyProjectionDao records = mock(CdsLegacyProjectionDao.class);
        SecurityInfoManager security = mock(SecurityInfoManager.class);
        RxManager rx = mock(RxManager.class);
        RxWebService service = new RxWebService() { @Override protected LoggedInInfo getLoggedInInfo() { return info; } };
        ReflectionTestUtils.setField(service, "securityInfoManager", security);
        ReflectionTestUtils.setField(service, "legacyProjection", new CdsLegacyProjectionService(records));
        ReflectionTestUtils.setField(service, "rxManager", rx);
        assertThrows(AccessDeniedException.class, () -> service.getAllDrugs(42));
        verifyNoInteractions(records, rx);
    }
    @Test void shouldRefuseAuthorizedRxProjection_beforeReturningNativeSubset() {
        LoggedInInfo info = new LoggedInInfo();
        CdsLegacyProjectionDao records = mock(CdsLegacyProjectionDao.class);
        when(records.hasSourceFields(42, Set.of("MedicationsAndTreatments"))).thenReturn(true);
        SecurityInfoManager security = mock(SecurityInfoManager.class);
        when(security.hasPrivilege(info, "_rx", "r", 42)).thenReturn(true);
        RxManager rx = mock(RxManager.class);
        RxWebService service = new RxWebService() { @Override protected LoggedInInfo getLoggedInInfo() { return info; } };
        ReflectionTestUtils.setField(service, "securityInfoManager", security);
        ReflectionTestUtils.setField(service, "legacyProjection", new CdsLegacyProjectionService(records));
        ReflectionTestUtils.setField(service, "rxManager", rx);
        var failure = assertThrows(WebApplicationException.class, () -> service.getAllDrugs(42));
        assertEquals(409, failure.getResponse().getStatus()); verifyNoInteractions(rx);
    }
    @Test void shouldAuthorizeAllergy_beforeProtectedRepresentationQuery() {
        CdsLegacyProjectionDao records = mock(CdsLegacyProjectionDao.class);
        AllergyManager manager = mock(AllergyManager.class);
        when(manager.getActiveAllergies(null, 42)).thenThrow(new SecurityException("denied"));
        AllergyService service = new AllergyService() { @Override protected LoggedInInfo getLoggedInInfo() { return null; } };
        ReflectionTestUtils.setField(service, "allergyManager", manager);
        ReflectionTestUtils.setField(service, "legacyProjection", new CdsLegacyProjectionService(records));
        assertThrows(SecurityException.class, () -> service.getCurrentAllergiesWithPrecision(42));
        verifyNoInteractions(records);
    }
    @Test void shouldNotHideOmittedImportedFields_withExactAllergyDatesInOldProjection() {
        CdsLegacyProjectionDao records = mock(CdsLegacyProjectionDao.class);
        when(records.hasSourceFields(42, Set.of("AllergiesAndAdverseReactions"))).thenReturn(true);
        AllergyManager manager = mock(AllergyManager.class);
        when(manager.getActiveAllergies(null, 42)).thenReturn(List.of(new Allergy()));
        AllergyService service = new AllergyService() { @Override protected LoggedInInfo getLoggedInInfo() { return null; } };
        ReflectionTestUtils.setField(service, "allergyManager", manager);
        ReflectionTestUtils.setField(service, "legacyProjection", new CdsLegacyProjectionService(records));
        var failure = assertThrows(WebApplicationException.class, () -> service.getCurrentAllergiesWithPrecision(42));
        assertEquals(409, failure.getResponse().getStatus());
    }
}
