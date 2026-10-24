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
package io.github.carlos_emr.carlos.integration.fhir.manager;

import io.github.carlos_emr.carlos.commn.interfaces.Immunization.ImmunizationProperty;
import io.github.carlos_emr.carlos.commn.model.Prevention;
import io.github.carlos_emr.carlos.commn.model.PreventionExt;
import io.github.carlos_emr.carlos.integration.fhir.model.Immunization;
import io.github.carlos_emr.carlos.managers.PreventionManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.hibernate.collection.spi.PersistentBag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for {@link OscarFhirResourceManager#getImmunizationById}: the FHIR
 * Immunization mapper reads lot/route/dose/site from {@code Prevention.preventionExts}, which
 * is lazy and unusable on the detached entity returned by {@code PreventionManager#getPrevention}.
 *
 * @since 2026-09-17
 */
@DisplayName("OscarFhirResourceManager immunization by id")
@Tag("unit")
class OscarFhirResourceManagerImmunizationUnitTest extends CarlosUnitTestBase {

    private static final int PREVENTION_ID = 42;

    @Test
    @DisplayName("should attach extensions from the manager before mapping a detached prevention")
    void shouldAttachPreventionExts_whenMappingImmunizationById() {
        PreventionManager preventionManager = createAndRegisterMock(PreventionManager.class);
        OscarFhirConfigurationManager configurationManager = mock(OscarFhirConfigurationManager.class);
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        when(configurationManager.getLoggedInInfo()).thenReturn(loggedInInfo);

        Prevention detached = new Prevention();
        ReflectionTestUtils.setField(detached, "id", PREVENTION_ID);
        detached.setDemographicId(7);
        detached.setPreventionType("Flu");
        detached.setPreventionDate(new Date());
        // An uninitialized Hibernate collection with no Session: iterating it throws
        // LazyInitializationException, exactly like the entity returned by PreventionDao#find.
        detached.setPreventionExts(new PersistentBag<>());

        PreventionExt lot = new PreventionExt();
        lot.setPreventionId(PREVENTION_ID);
        lot.setKeyval(ImmunizationProperty.lot.name());
        lot.setVal("LOT-9");
        when(preventionManager.getPrevention(loggedInInfo, PREVENTION_ID)).thenReturn(detached);
        when(preventionManager.getPreventionExtByPrevention(loggedInInfo, PREVENTION_ID)).thenReturn(List.of(lot));

        Immunization<Prevention> immunization =
                OscarFhirResourceManager.getImmunizationById(configurationManager, PREVENTION_ID);

        assertThat(immunization).isNotNull();
        assertThat(immunization.getFhirResource().getLotNumber()).isEqualTo("LOT-9");
        verify(preventionManager).getPreventionExtByPrevention(loggedInInfo, PREVENTION_ID);
    }
}
