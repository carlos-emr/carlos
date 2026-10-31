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

import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsLegacyProjectionService;
import io.github.carlos_emr.carlos.commn.model.CdsLegacyProjectionException;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class CdsLegacyFhirProjectionUnitTest extends io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase {
    private DemographicManager patients;
    private OscarFhirConfigurationManager context;
    private LoggedInInfo actor;
    private Demographic patient;
    @BeforeEach
    void prepareAuthorizedImportedPatient() {
        patients=createAndRegisterMock(DemographicManager.class);
        var projections=createAndRegisterMock(CdsLegacyProjectionService.class);
        context=mock(OscarFhirConfigurationManager.class); actor=mock(LoggedInInfo.class);
        when(context.getLoggedInInfo()).thenReturn(actor);
        patient=new Demographic(); patient.setDemographicNo(51);
        doThrow(new CdsLegacyProjectionException()).when(projections).requireSupported(51,Set.of("Demographics"));
    }
    @Test
    void shouldRefusePatientResource_whenStructuredDemographicsCannotBeRepresented() {
        when(patients.getDemographic(actor,51)).thenReturn(patient);
        assertThatThrownBy(()->OscarFhirResourceManager.getPatientByDemographicNumber(context,51))
                .isInstanceOf(ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException.class)
                .hasMessageContaining("cannot represent");
    }
    @Test
    void shouldRefusePatientSearchBeforeResourceConstruction_whenImportedDemographicsWouldBeOmitted() {
        when(patients.getActiveDemosByHealthCardNo(actor,"test","ON")).thenReturn(List.of(patient));
        assertThatThrownBy(()->OscarFhirResourceManager.getPatientsByPHN(context,"test","ON"))
                .isInstanceOf(ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException.class)
                .hasMessageContaining("cannot represent");
    }
}
