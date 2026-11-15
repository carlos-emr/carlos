/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.webserv;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import io.github.carlos_emr.carlos.commn.model.Document;
import io.github.carlos_emr.carlos.managers.DocumentManager;
import io.github.carlos_emr.carlos.test.base.CarlosSoapTestBase;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.webserv.transfer_objects.DocumentTransfer;
import jakarta.xml.ws.BindingProvider;
import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.apache.cxf.message.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

/**
 * The three bulk {@link DocumentWs} operations over the real CXF pipeline. A request without the
 * {@code X-Carlos-Document-List-Profile} header is an unmodified client and must receive what it
 * always received: file content inline. The header only adds opt-in behaviour.
 */
@Tag("unit")
class DocumentListContentEndpointTest extends CarlosSoapTestBase {
    @Mock private DocumentManager documents;
    @Override protected Object getServiceBean() {
        var service = new DocumentWs(); injectDependency(service, "documentManager", documents); return service;
    }
    @Override protected Class<?> getServiceInterface() { return DocumentWs.class; }
    @BeforeEach void dependencies() { registerMock(DocumentManager.class, documents); }

    /** A stored document whose file cannot be read; the failure message stands in for a private filename. */
    private Document row(int id) throws IOException {
        Document result = spy(new Document(id)); result.setDocdesc("Retained metadata");
        doThrow(new IOException("PRIVATE_patient_report.pdf")).when(result).getDocumentFileContentsAsBytes();
        return result;
    }
    private Document readable(int id, byte... content) throws IOException {
        Document result = row(id); doReturn(content).when(result).getDocumentFileContentsAsBytes(); return result;
    }
    private DocumentWs proxy(String... profile) {
        DocumentWs proxy = createClient(DocumentWs.class);
        if (profile.length > 0) ((BindingProvider) proxy).getRequestContext()
                .put(Message.PROTOCOL_HEADERS, Map.of(DocumentListProfile.HEADER, List.of(profile)));
        return proxy;
    }
    @SuppressWarnings("unchecked")
    private static List<String> responseHeader(DocumentWs proxy, String name) {
        var headers = (Map<String, List<String>>) ((BindingProvider) proxy).getResponseContext().get(Message.PROTOCOL_HEADERS);
        for (var header : headers.entrySet()) if (name.equalsIgnoreCase(header.getKey())) return header.getValue();
        return null;
    }
    private void stubPages(List<Document> page) {
        when(documents.getDocumentsUpdateAfterDate(any(), any(), anyInt())).thenReturn(page);
        when(documents.getDocumentsByProgramProviderDemographicDate(any(), any(), any(), any(), any(), anyInt())).thenReturn(page);
        when(documents.getDocumentsByDemographicIdUpdateAfterDate(any(), any(), any())).thenReturn(page);
    }
    /** Every bulk operation, so a profile cannot be right for one and wrong for another. */
    private static List<Function<DocumentWs, DocumentTransfer[]>> bulkOperations() {
        return List.of(proxy -> proxy.getDocumentsUpdateAfterDate(new Date(), 2),
                proxy -> proxy.getDocumentsByProgramProviderDemographicDate(1, "p", 42, Calendar.getInstance(), 2),
                proxy -> proxy.getDocumentsByDemographicIdAfter(Calendar.getInstance(), 42));
    }

    @Test void shouldReturnFileContentsInline_forHeaderlessCallsOnEveryBulkOperation() throws Exception {
        stubPages(List.of(readable(1, (byte) 1, (byte) 2, (byte) 3), readable(2, (byte) 4, (byte) 5)));
        for (var operation : bulkOperations()) {
            DocumentWs proxy = proxy();
            DocumentTransfer[] page = operation.apply(proxy);
            assertThat(page).extracting(DocumentTransfer::getDocumentNo).containsExactly(1, 2);
            assertThat(page[0].getFileContents()).containsExactly(1, 2, 3);
            assertThat(page[1].getFileContents()).containsExactly(4, 5);
            assertThat(page).allSatisfy(row -> assertThat(row.getDocdesc()).isEqualTo("Retained metadata"));
            // Nothing was selected, so nothing is acknowledged.
            assertThat(responseHeader(proxy, DocumentListProfile.HEADER)).isNull();
        }
    }
    @Test void shouldLeaveAnUnreadableDocumentOut_forHeaderlessCallsAsBefore() throws Exception {
        stubPages(List.of(readable(1, (byte) 1), row(2), readable(3, (byte) 3)));
        try (LogCapture log = LogCapture.forLogger(DocumentTransfer.class)) {
            for (var operation : bulkOperations()) {
                DocumentTransfer[] page = operation.apply(proxy());
                // The published behaviour: no fault for the page, the unreadable item is absent.
                assertThat(page).extracting(DocumentTransfer::getDocumentNo).containsExactly(1, 3);
                assertThat(page[0].getFileContents()).containsExactly(1);
                assertThat(page[1].getFileContents()).containsExactly(3);
            }
            // An operator can find the document; the private filename in the failure is not written.
            assertThat(log.messages()).hasSize(3).allSatisfy(message ->
                    assertThat(message).contains("documentNo=2").doesNotContain("PRIVATE"));
        }
    }
    @Test void shouldReturnEveryMetadataRow_whenTheMetadataProfileIsExplicit() throws Exception {
        Document first = row(1), second = row(2); stubPages(List.of(first, second));
        for (var operation : bulkOperations()) {
            DocumentWs proxy = proxy("metadata");
            DocumentTransfer[] page = operation.apply(proxy);
            assertThat(page).extracting(DocumentTransfer::getDocumentNo).containsExactly(1, 2);
            assertThat(page).allSatisfy(row -> {
                assertThat(row.getFileContents()).isNull(); assertThat(row.getDocdesc()).isEqualTo("Retained metadata");
            });
            assertThat(responseHeader(proxy, DocumentListProfile.HEADER)).containsExactly("metadata");
        }
        verify(first, never()).getDocumentFileContentsAsBytes(); verify(second, never()).getDocumentFileContentsAsBytes();
    }
    @Test void shouldRetainInlineBytes_whenLegacyProfileIsExplicit() throws Exception {
        stubPages(List.of(readable(1, (byte) 1, (byte) 2, (byte) 3), readable(2, (byte) 4, (byte) 5)));
        DocumentWs proxy = proxy("legacy-inline");
        DocumentTransfer[] page = proxy.getDocumentsUpdateAfterDate(new Date(),2);
        assertThat(page).hasSize(2); assertThat(page[0].getFileContents()).containsExactly(1,2,3);
        assertThat(page[1].getFileContents()).containsExactly(4,5);
        assertThat(responseHeader(proxy, DocumentListProfile.HEADER)).containsExactly("legacy-inline");
    }
    @Test void shouldFaultTheWholePage_whenTheExplicitLegacyProfileMeetsAnUnreadableDocument() throws Exception {
        Document first = readable(1, (byte) 1), second = row(2); stubPages(List.of(first,second));
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
