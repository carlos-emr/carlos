/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.webserv.rest;

import io.github.carlos_emr.carlos.inbox.InboxManagerQuery;
import io.github.carlos_emr.carlos.inbox.InboxManagerResponse;
import io.github.carlos_emr.carlos.lab.ca.on.LabResultData;
import io.github.carlos_emr.carlos.managers.InboxManager;
import io.github.carlos_emr.carlos.test.base.CarlosRestTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.ws.rs.core.Response;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("integration")
@Tag("rest")
@Tag("security")
class InboxIdentifierDisclosureEndpointTest extends CarlosRestTestBase {
    @Mock private InboxManager inbox;

    @Override protected Object getServiceBean() {
        var service = new InboxService() {
            @Override public int getMyUnacknowlegedReportsCount() { return 7; }
        };
        injectDependency(service, "inboxManager", inbox);
        return service;
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldReturnTheHealthCardNumber_withInboxSelectionMetadata(boolean document) {
        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn("41");
        var item = mock(LabResultData.class);
        when(item.getSegmentID()).thenReturn("17");
        when(item.getLabPatientId()).thenReturn("42");
        when(item.getPatientName()).thenReturn("Synthetic Patient");
        when(item.isDocument()).thenReturn(document);
        if (document) when(item.getDiscipline()).thenReturn("Synthetic document type");
        else when(item.getDisciplineDisplayString()).thenReturn("Synthetic laboratory type");
        when(item.getPriority()).thenReturn("U");
        when(item.isFinal()).thenReturn(true);
        when(item.getDateTime()).thenReturn("2026-08-01 12:00:00");
        when(item.getHealthNumber()).thenReturn("FAKE-HIN");
        var page = new InboxManagerResponse(); page.setLabdocs(List.of(item));
        when(inbox.getInboxResults(any(LoggedInInfo.class), any(InboxManagerQuery.class))).thenReturn(page);
        try (Response response = request().path("/inbox/mine").query("limit", 5).get()) {
            assertThat(response.getStatus()).isEqualTo(200);
            var payload = responseJson(response);
            assertThat(payload.path("total").asInt()).isEqualTo(7);
            assertThat(payload.path("limit").asInt()).isEqualTo(5);
            assertThat(payload.path("offset").asInt()).isZero();
            assertThat(payload.path("inbox")).as("Inbox wire payload: %s", payload).hasSize(1);
            var record = payload.path("inbox").get(0);
            assertThat(record.path("id").asInt()).isEqualTo(17);
            assertThat(record.path("demographicNo").asInt()).isEqualTo(42);
            assertThat(record.path("demographicName").asText()).isEqualTo("Synthetic Patient");
            assertThat(record.path("status").asText()).isEqualTo("Final");
            assertThat(record.path("priority").asText()).isEqualTo("U");
            assertThat(record.path("discipline").asText()).isEqualTo(document ? "Synthetic document type" : "Synthetic laboratory type");
            // Inbox clients match a result to its patient by health card number; the published item carries it.
            assertThat(record.path("hin").asText()).isEqualTo("FAKE-HIN");
        }
        verify(inbox).getInboxResults(eq(mockLoggedInInfo), argThat(query -> "41".equals(query.getProviderNo())
                && "41".equals(query.getSearchProviderNo()) && query.getPage() == 0 && query.getPageSize() == 20));
    }
}
