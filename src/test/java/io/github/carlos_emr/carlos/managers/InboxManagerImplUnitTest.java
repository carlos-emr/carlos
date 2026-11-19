/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.managers;

import io.github.carlos_emr.carlos.PMmodule.dao.SecUserRoleDao;
import io.github.carlos_emr.carlos.commn.dao.DocumentResultsDao;
import io.github.carlos_emr.carlos.commn.dao.InboxDocumentResults;
import io.github.carlos_emr.carlos.commn.dao.InboxResultsDao;
import io.github.carlos_emr.carlos.commn.dao.QueueDocumentLinkDao;
import io.github.carlos_emr.carlos.commn.model.Queue;
import io.github.carlos_emr.carlos.commn.model.QueueDocumentLink;
import io.github.carlos_emr.carlos.inbox.InboxManagerQuery;
import io.github.carlos_emr.carlos.lab.ca.on.CommonLabResultData;
import io.github.carlos_emr.carlos.lab.ca.on.HRMResultsData;
import io.github.carlos_emr.carlos.lab.ca.on.LabResultData;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class InboxManagerImplUnitTest {
    @Mock private QueueDocumentLinkDao queues;
    @Mock private SecUserRoleDao roles;
    @Mock private InboxResultsDao inbox;
    @Mock private DocumentResultsDao documents;
    @Mock private LoggedInInfo user;
    @InjectMocks private InboxManagerImpl manager;

    @Test
    void shouldReturnValidDocumentsAndAnOmissionCount_whenARoutedDocumentIsUnavailable() {
        var query = new InboxManagerQuery();
        query.setView(InboxManager.DOCUMENTS);
        when(user.getLoggedInProviderNo()).thenReturn("74");
        when(roles.getUserRoles("74")).thenReturn(List.of());

        var link = new QueueDocumentLink();
        link.setDocId(52);
        link.setQueueId(Queue.DEFAULT_QUEUE_ID);
        when(queues.getQueueDocLinks()).thenReturn(List.of(link));
        when(documents.isSentToValidProvider("52")).thenReturn(true);
        when(documents.isSentToProvider("52", "74")).thenReturn(true);
        try (var spring = mockStatic(SpringUtils.class);
             var labs = mockConstruction(CommonLabResultData.class);
             var hrm = mockConstruction(HRMResultsData.class, (reader, context) -> when(
                    reader.populateHRMdocumentsResultsData(user, "74", "N", null, null, true, 0, 20))
                    .thenReturn(List.of()))) {
            spring.when(() -> SpringUtils.getBean(InboxResultsDao.class)).thenReturn(inbox);
            spring.when(() -> SpringUtils.getBean(DocumentResultsDao.class)).thenReturn(documents);
            var document = new LabResultData();
            document.setSegmentID("52");
            document.setAcknowledgedStatus("N");
            document.setLabPatientId("13");
            document.patientName = "Synthetic patient";
            document.labType = "DOC";
            when(inbox.readDocumentResults("74", null, null, null, null, "N", true, 0, 20,
                    false, null, null, null, null)).thenReturn(new InboxDocumentResults(List.of(document), 1));

            var response = manager.getInboxResults(user, query);

            assertThat(response.getLabdocs()).containsExactly(document);
            assertThat(response.getOmittedDocumentRows()).isEqualTo(1);
        }
    }
}
