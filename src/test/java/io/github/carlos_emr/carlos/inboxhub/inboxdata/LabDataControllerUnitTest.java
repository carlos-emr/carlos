/*
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.carlos_emr.carlos.inboxhub.inboxdata;

import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.QueueDocumentLinkDao;
import io.github.carlos_emr.carlos.inboxhub.query.InboxhubQuery;
import io.github.carlos_emr.carlos.lab.ca.on.CommonLabResultData;
import io.github.carlos_emr.carlos.lab.ca.on.LabResultData;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;

/** Guards against silently dropping status-filtered or unrelated patient reports after pagination. */
@Tag("unit")
@Tag("lab")
class LabDataControllerUnitTest extends CarlosUnitTestBase {
    @Test
    void shouldOpenSelectedReport_whenLinkingHistoricalLabVersion() {
        createAndRegisterMock(PatientLabRoutingDao.class);
        createAndRegisterMock(ProviderLabRoutingDao.class);
        createAndRegisterMock(QueueDocumentLinkDao.class);
        createAndRegisterMock(SecurityInfoManager.class);
        LabResultData report = report("10", "Patient A", "F");
        report.discipline = "CHEM";
        report.resultStatus = "F";
        assertThat(new LabDataController().getLabLink(new ArrayList<>(List.of(report)),
                new InboxhubQuery(), "/carlos", "999998"))
                .singleElement().asString().contains("segmentID=10", "showLatest=false");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "N", "A", "F"})
    void shouldPreserveEverySelectedReport_withSharedAccessionNumbers(String status) {
        createAndRegisterMock(PatientLabRoutingDao.class);
        createAndRegisterMock(ProviderLabRoutingDao.class);
        createAndRegisterMock(QueueDocumentLinkDao.class);
        createAndRegisterMock(SecurityInfoManager.class);
        LabResultData first = report("10", "Patient A", "N");
        LabResultData second = report("11", "Patient A", "F");
        LabResultData unrelated = report("12", "Patient B", "N");
        ArrayList<LabResultData> selected = new ArrayList<>(List.of(first, second, unrelated));
        if (!status.isEmpty()) {
            selected.removeIf(report -> !status.equals(report.acknowledgedStatus));
        }
        try (var construction = mockConstruction(CommonLabResultData.class, (mock, context) ->
                when(mock.populateLabResultsData(any(), anyString(), any(), anyString(), anyString(),
                        anyString(), anyString(), anyBoolean(), anyInt(), anyInt(), anyBoolean(),
                        any(), any(), any())).thenReturn(new ArrayList<>(selected)))) {
            InboxhubQuery query = new InboxhubQuery();
            query.setLab(true);
            query.setPage(1);
            query.setPageSize(20);
            query.setStatus(status);
            assertThat(new LabDataController().getLabData(null, query)).containsExactlyElementsOf(selected);
        }
    }

    private static LabResultData report(String id, String patient, String status) {
        LabResultData report = new LabResultData();
        report.setSegmentID(id);
        report.labType = "HL7";
        report.accessionNumber = "SHARED-ACCESSION";
        report.patientName = patient;
        report.acknowledgedStatus = status;
        report.setDateObj(new Date(1700000000000L));
        return report;
    }
}
