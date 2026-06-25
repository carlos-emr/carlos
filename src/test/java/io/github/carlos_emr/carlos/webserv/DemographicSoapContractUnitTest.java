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
package io.github.carlos_emr.carlos.webserv;

import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.transfer_objects.DemographicTransfer;
import io.github.carlos_emr.carlos.webserv.transfer_objects.DemographicTransfer2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Contract tests for the native CARLOS {@code DemographicService} SOAP operations behind the
 * patient/demographic rows of {@code docs/api/cortico-carlos-compatibility.md}.
 *
 * <p>Each test exercises a representative successful call and proves the operation delegates to
 * {@link DemographicManager} with the native CARLOS contract arguments. These are native CARLOS
 * compatibility behaviors; mapping literal Cortico/Juno {@code .ws} demographic operation labels
 * onto them is an adapter/proxy responsibility and is out of scope here.</p>
 *
 * <p>Fixtures are synthetic; no PHI, credentials, or live calls are used.</p>
 *
 * @since 2026-06-25
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Native DemographicService SOAP contract")
@Tag("unit")
@Tag("webservice")
class DemographicSoapContractUnitTest extends CarlosUnitTestBase {

    private static final Integer DEMOGRAPHIC_ID = 654;

    @Mock
    private DemographicManager demographicManager;

    private LoggedInInfo loggedInInfo;
    private DemographicWs service;

    @BeforeEach
    void setUp() {
        loggedInInfo = new LoggedInInfo();
        service = new DemographicWs() {
            @Override
            protected LoggedInInfo getLoggedInInfo() {
                return loggedInInfo;
            }
        };
        injectDependency(service, "demographicManager", demographicManager);
    }

    @Test
    @DisplayName("should read demographic with extensions when getDemographic is called")
    void shouldReadDemographic_whenLookingUpById() {
        Demographic demographic = new Demographic();
        demographic.setDemographicNo(DEMOGRAPHIC_ID);
        when(demographicManager.getDemographicWithExt(loggedInInfo, DEMOGRAPHIC_ID)).thenReturn(demographic);

        DemographicTransfer result = service.getDemographic(DEMOGRAPHIC_ID);

        assertThat(result).isNotNull();
        assertThat(result.getDemographicNo()).isEqualTo(DEMOGRAPHIC_ID);
        verify(demographicManager).getDemographicWithExt(loggedInInfo, DEMOGRAPHIC_ID);
    }

    @Test
    @DisplayName("should read demographic when getDemographic2 is called")
    void shouldReadDemographic_whenLookingUpByIdVersion2() {
        Demographic demographic = new Demographic();
        demographic.setDemographicNo(DEMOGRAPHIC_ID);
        when(demographicManager.getDemographic(loggedInInfo, DEMOGRAPHIC_ID)).thenReturn(demographic);

        DemographicTransfer2 result = service.getDemographic2(DEMOGRAPHIC_ID);

        assertThat(result).isNotNull();
        assertThat(result.getDemographicNo()).isEqualTo(DEMOGRAPHIC_ID);
        verify(demographicManager).getDemographic(loggedInInfo, DEMOGRAPHIC_ID);
    }

    @Test
    @DisplayName("should search by name when searchDemographicByName is called")
    void shouldSearchByName_whenSearchingDemographics() {
        when(demographicManager.searchDemographicByName(loggedInInfo, "smith", 0, 25))
                .thenReturn(Collections.emptyList());

        DemographicTransfer[] result = service.searchDemographicByName("smith", 0, 25);

        assertThat(result).isEmpty();
        verify(demographicManager).searchDemographicByName(loggedInInfo, "smith", 0, 25);
    }

    @Test
    @DisplayName("should search by attributes when get_patient_by_attributes maps to searchDemographicsByAttributes")
    void shouldSearchByAttributes_whenSearchingByPhnOrEmail() {
        when(demographicManager.searchDemographicsByAttributes(eq(loggedInInfo), eq("9999999999"),
                isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                eq("patient@example.test"), isNull(), eq(0), eq(25)))
                .thenReturn(Collections.emptyList());

        DemographicTransfer[] result = service.searchDemographicsByAttributes("9999999999", null, null,
                null, null, null, null, null, "patient@example.test", null, 0, 25);

        assertThat(result).isEmpty();
        verify(demographicManager).searchDemographicsByAttributes(eq(loggedInInfo), eq("9999999999"),
                isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                eq("patient@example.test"), isNull(), eq(0), eq(25));
    }
}
