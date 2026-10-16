/*
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.carlos_emr.carlos.inboxhub.inboxdata;

import io.github.carlos_emr.carlos.commn.dao.InboxResultsDao;
import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.QueueDocumentLinkDao;
import io.github.carlos_emr.carlos.inboxhub.query.InboxhubQuery;
import io.github.carlos_emr.carlos.lab.ca.on.CommonLabResultData;
import io.github.carlos_emr.carlos.lab.ca.on.HRMResultsData;
import io.github.carlos_emr.carlos.lab.ca.on.LabResultData;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
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
                        any(), any(), any(), any())).thenReturn(new ArrayList<>(selected)))) {
            InboxhubQuery query = new InboxhubQuery();
            query.setLab(true);
            query.setPage(1);
            query.setPageSize(20);
            query.setStatus(status);
            assertThat(new LabDataController().getLabData(null, query)).containsExactlyElementsOf(selected);
        }
    }

    /**
     * The toolbar's Acknowledged toggle lists what was acknowledged today. Each source has to get
     * the same bound -- the start of the clinic's day -- or the one that does not pages in its
     * whole acknowledged history, which is what left an alpha tester's inbox loading until Stop.
     */
    @Test
    void shouldBoundEverySourceToStartOfToday_whenToolbarListsAcknowledgedResults() {
        // 14:30 on 2026-10-15 in Toronto (EDT, UTC-4): the clinic's day began at 04:00 UTC.
        Clock clock = Clock.fixed(Instant.parse("2026-10-15T18:30:00Z"), ZoneId.of("America/Toronto"));
        InboxhubQuery query = allTypesQuery("A");
        query.setAcknowledgedToday(true);

        assertThat(boundsPassedToEachSource(new LabDataController(clock), query))
                .containsExactly(Date.from(Instant.parse("2026-10-15T04:00:00Z")),
                        Date.from(Instant.parse("2026-10-15T04:00:00Z")),
                        Date.from(Instant.parse("2026-10-15T04:00:00Z")));
    }

    /**
     * Only the toggle's own Acknowledged list is bounded. A New, Filed or All search is never
     * narrowed to today, and an Acknowledged search made from the search panel -- the way to
     * reach older acknowledgements -- keeps its full reach.
     */
    @ParameterizedTest
    @CsvSource(value = {"A,false", "N,true", "F,true", "'',true", "N,false"})
    void shouldNotBoundAnySource_unlessToolbarListsAcknowledgedResults(String status, boolean acknowledgedToday) {
        Clock clock = Clock.fixed(Instant.parse("2026-10-15T18:30:00Z"), ZoneId.of("America/Toronto"));
        InboxhubQuery query = allTypesQuery(status);
        query.setAcknowledgedToday(acknowledgedToday);

        assertThat(boundsPassedToEachSource(new LabDataController(clock), query)).containsExactly(null, null, null);
    }

    private static InboxhubQuery allTypesQuery(String status) {
        InboxhubQuery query = new InboxhubQuery();
        query.setStatus(status);
        query.setSearchProviderNo("999998");
        query.setPage(1);
        query.setPageSize(20);
        return query;
    }

    /** The acknowledgement bound each source received: documents, labs, then HRM reports. */
    private List<Date> boundsPassedToEachSource(LabDataController controller, InboxhubQuery query) {
        // CommonLabResultData's static fields read these beans. Without them, a JVM where this is
        // the first test to touch the class fails to initialize it for every later test too.
        createAndRegisterMock(PatientLabRoutingDao.class);
        createAndRegisterMock(ProviderLabRoutingDao.class);
        createAndRegisterMock(QueueDocumentLinkDao.class);
        createAndRegisterMock(SecurityInfoManager.class);
        InboxResultsDao documents = mock(InboxResultsDao.class);
        springUtilsMock.when(() -> SpringUtils.getBean("inboxResultsDao")).thenReturn(documents);
        ArgumentCaptor<Date> documentBound = ArgumentCaptor.forClass(Date.class);
        ArgumentCaptor<Date> labBound = ArgumentCaptor.forClass(Date.class);
        ArgumentCaptor<Date> hrmBound = ArgumentCaptor.forClass(Date.class);
        try (var labs = mockConstruction(CommonLabResultData.class);
             var hrm = mockConstruction(HRMResultsData.class)) {
            controller.getLabData(null, query);

            verify(documents).populateDocumentResultsData(eq("999998"), isNull(), eq(""), eq(""), eq(""),
                    eq(query.getStatus()), eq(true), eq(0), eq(20), eq(false), isNull(), isNull(), isNull(),
                    documentBound.capture());
            verify(labs.constructed().get(0)).populateLabResultsData(isNull(), eq("999998"), isNull(), eq(""),
                    eq(""), eq(""), eq(query.getStatus()), eq(true), eq(0), eq(100), eq(false), isNull(), isNull(),
                    isNull(), labBound.capture());
            verify(hrm.constructed().get(0)).populateHRMdocumentsResultsData(isNull(), eq("999998"), eq(""), eq(""),
                    eq(""), isNull(), eq(query.getStatus()), isNull(), isNull(), eq(true), eq(0), eq(20),
                    hrmBound.capture());
        }
        return Arrays.asList(documentBound.getValue(), labBound.getValue(), hrmBound.getValue());
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
