/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.lab.ca.bc.PathNet;

import io.github.carlos_emr.carlos.billing.CA.BC.dao.*;
import io.github.carlos_emr.carlos.billing.CA.BC.model.*;
import io.github.carlos_emr.carlos.billing.CA.BC.util.PathNetLabResults;
import io.github.carlos_emr.carlos.commn.dao.ConsultDocsDao;
import io.github.carlos_emr.carlos.commn.dao.ConsultResponseDocDao;
import io.github.carlos_emr.carlos.commn.dao.EFormDocsDao;
import io.github.carlos_emr.carlos.commn.model.PatientLabRouting;
import io.github.carlos_emr.carlos.commn.model.ProviderLabRoutingModel;
import io.github.carlos_emr.carlos.lab.ca.on.LabResultData;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;

import java.util.Date;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** Exercise all consumers of the two PathNet constructor projections. */
@DisplayName("PathNet lab list status conversion")
class PathnetResultsDataUnitTest extends CarlosUnitTestBase {
    private Hl7MshDao dao;

    @BeforeEach
    void setUp() {
        dao = createAndRegisterMock(Hl7MshDao.class);
        createAndRegisterMock(ConsultDocsDao.class);
        createAndRegisterMock(ConsultResponseDocDao.class);
        createAndRegisterMock(EFormDocsDao.class);
        createAndRegisterMock(Hl7MessageDao.class);
        createAndRegisterMock(Hl7ObrDao.class);
        createAndRegisterMock(Hl7ObxDao.class);
        createAndRegisterMock(Hl7OrcDao.class);
        createAndRegisterMock(Hl7PidDao.class);
    }

    static Stream<Arguments> statusesAndRoutes() {
        return Stream.of("provider", "patient", "lab").flatMap(route ->
                Stream.of("F", "P", "C", "", "007", (String) null)
                        .map(status -> Arguments.of(route, status)));
    }

    @ParameterizedTest
    @MethodSource("statusesAndRoutes")
    void shouldRetainLabAndFinalFlag_whenStatusIsTextual(String route, String status) {
        Hl7Msh msh = new Hl7Msh();
        msh.setDateTime(new Date(0));
        Hl7Pid pid = new Hl7Pid();
        pid.setMessageId(9001);
        pid.setPatientName("FAKE^PATHNET");
        pid.setExternalId("FAKE-HIN");
        pid.setSex("F");
        Hl7Orc orc = new Hl7Orc();
        orc.setFillerOrderNumber("FAKE-ACCESSION-TEST");
        orc.setOrderingProvider("000000^FAKE^PROVIDER");
        ProviderLabRoutingModel providerRouting = new ProviderLabRoutingModel();
        providerRouting.setStatus("N");
        PathNetLabResults projected = "patient".equals(route)
                ? new PathNetLabResults(msh, pid, orc, new Hl7Obr(), new PatientLabRouting(), status)
                : new PathNetLabResults(msh, pid, orc, new Hl7Obr(), providerRouting, status);
        String demographic = null;
        Integer labNo = null;
        if ("patient".equals(route)) {
            demographic = "42";
            when(dao.findPathnetResultsDeomgraphicNo(42, "BCP")).thenReturn(List.of(projected));
        } else if ("lab".equals(route)) {
            labNo = 9001;
            when(dao.findPathnetResultsByLabNo(9001)).thenReturn(List.of(projected));
        } else {
            when(dao.findPathnetResultsDataByPatientNameHinStatusAndProvider("%^%", "%%", "%%", "999998", "BCP"))
                    .thenReturn(List.of(projected));
        }

        List<LabResultData> results = new PathnetResultsData()
                .populatePathnetResultsData("999998", demographic, null, null, null, null, labNo);

        assertThat(results).hasSize(1);
        LabResultData result = results.get(0);
        assertThat(result.segmentID).isEqualTo("9001");
        assertThat(result.reportStatus).isEqualTo(status);
        assertThat(result.finalRes).isEqualTo("F".equals(status));
        assertThat(result.patientName).isEqualTo("FAKE PATHNET");
        assertThat(result.acknowledgedStatus).isEqualTo("patient".equals(route) ? "U" : "N");
    }
}
