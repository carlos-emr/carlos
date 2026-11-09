/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.webserv;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import io.github.carlos_emr.carlos.commn.model.Document;
import io.github.carlos_emr.carlos.managers.DocumentManager;
import io.github.carlos_emr.carlos.test.base.CarlosSoapTestBase;
import io.github.carlos_emr.carlos.webserv.transfer_objects.DocumentTransfer;
import jakarta.xml.ws.BindingProvider;
import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.apache.cxf.message.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

@Tag("unit")
class DocumentListContentEndpointTest extends CarlosSoapTestBase {
    @Mock private DocumentManager documents;
    @Override protected Object getServiceBean() {
        var service = new DocumentWs(); injectDependency(service, "documentManager", documents); return service;
    }
    @Override protected Class<?> getServiceInterface() { return DocumentWs.class; }
    @BeforeEach void dependencies() { registerMock(DocumentManager.class, documents); }

    private Document row(int id) throws IOException {
        Document result = spy(new Document(id)); result.setDocdesc("Retained metadata");
        doThrow(new IOException("PRIVATE_patient_report.pdf")).when(result).getDocumentFileContentsAsBytes();
        return result;
    }
    private DocumentWs proxy(String... profile) {
        DocumentWs proxy = createClient(DocumentWs.class);
        if (profile.length > 0) ((BindingProvider) proxy).getRequestContext()
                .put(Message.PROTOCOL_HEADERS, Map.of(DocumentListProfile.HEADER, List.of(profile)));
        return proxy;
    }
    private void stubPages(List<Document> page) {
        when(documents.getDocumentsUpdateAfterDate(any(), any(), anyInt())).thenReturn(page);
        when(documents.getDocumentsByProgramProviderDemographicDate(any(), any(), any(), any(), any(), anyInt())).thenReturn(page);
        when(documents.getDocumentsByDemographicIdUpdateAfterDate(any(), any(), any())).thenReturn(page);
    }
    private void metadata(DocumentTransfer[] page) {
        assertThat(page).hasSize(2);
        assertThat(page).extracting(DocumentTransfer::getDocumentNo).containsExactly(1, 2);
        assertThat(page).allSatisfy(row -> {
            assertThat(row.getFileContents()).isNull(); assertThat(row.getDocdesc()).isEqualTo("Retained metadata");
        });
    }
    @Test void shouldReturnEveryMetadataRow_withoutReadingAnyFiles_onEveryBulkOperation() throws Exception {
        Document first = row(1), second = row(2); stubPages(List.of(first, second));
        DocumentWs proxy = proxy();
        metadata(proxy.getDocumentsUpdateAfterDate(new Date(), 2));
        metadata(proxy.getDocumentsByProgramProviderDemographicDate(1, "p", 42, Calendar.getInstance(), 2));
        metadata(proxy.getDocumentsByDemographicIdAfter(Calendar.getInstance(), 42));
        verify(first, never()).getDocumentFileContentsAsBytes(); verify(second, never()).getDocumentFileContentsAsBytes();
        @SuppressWarnings("unchecked")
        Map<String,List<String>> headers = (Map<String,List<String>>) ((BindingProvider) proxy)
                .getResponseContext().get(Message.PROTOCOL_HEADERS);
        assertThat(headers).containsEntry(DocumentListProfile.HEADER, List.of("metadata"));
    }
    @Test void shouldRetainInlineBytes_whenLegacyProfileIsExplicit() throws Exception {
        Document first = row(1), second = row(2);
        doReturn(new byte[]{1,2,3}).when(first).getDocumentFileContentsAsBytes();
        doReturn(new byte[]{4,5}).when(second).getDocumentFileContentsAsBytes();
        stubPages(List.of(first, second));
        DocumentWs proxy = proxy("legacy-inline");
        DocumentTransfer[] page = proxy.getDocumentsUpdateAfterDate(new Date(),2);
        assertThat(page).hasSize(2); assertThat(page[0].getFileContents()).containsExactly(1,2,3);
        assertThat(page[1].getFileContents()).containsExactly(4,5);
        @SuppressWarnings("unchecked")
        Map<String,List<String>> headers = (Map<String,List<String>>) ((BindingProvider) proxy)
                .getResponseContext().get(Message.PROTOCOL_HEADERS);
        assertThat(headers).containsEntry(DocumentListProfile.HEADER,List.of("legacy-inline"));
    }
    @Test void shouldFaultTheWholeLegacyPage_insteadOfSilentlyDroppingAnUnreadableDocument() throws Exception {
        Document first = row(1), second = row(2);
        doReturn(new byte[]{1}).when(first).getDocumentFileContentsAsBytes(); stubPages(List.of(first,second));
        assertThatThrownBy(() -> proxy("legacy-inline").getDocumentsUpdateAfterDate(new Date(),2))
                .hasMessageContaining("Document content could not be read").hasMessageNotContaining("PRIVATE");
        verify(second).getDocumentFileContentsAsBytes();
    }
    @Test void shouldFaultSingleDocumentRead_withoutExposingTheFilename() throws Exception {
        Document unreadable = row(1);
        when(documents.getDocument(any(),eq(1))).thenReturn(unreadable);
        assertThatThrownBy(() -> proxy().getDocument(1)).hasMessageContaining("Document content could not be read")
                .hasMessageNotContaining("PRIVATE");
    }
    @Test void shouldRejectInvalidAndDuplicateProfiles_beforeQueryingDocuments() {
        for (String value : List.of("", "unknown", "metadata,legacy-inline")) {
            assertThatThrownBy(() -> proxy(value).getDocumentsUpdateAfterDate(new Date(),2))
                    .hasMessageContaining("Unsupported document list profile");
        }
        assertThatThrownBy(() -> proxy("metadata","legacy-inline").getDocumentsUpdateAfterDate(new Date(),2))
                .hasMessageContaining("Unsupported document list profile");
        verifyNoInteractions(documents);
    }
}
