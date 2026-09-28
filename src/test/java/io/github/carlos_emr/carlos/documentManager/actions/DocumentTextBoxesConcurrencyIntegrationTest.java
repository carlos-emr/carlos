// SPDX-License-Identifier: GPL-2.0-or-later
package io.github.carlos_emr.carlos.documentManager.actions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.documentManager.EDoc;
import io.github.carlos_emr.carlos.documentManager.EDocUtil;
import io.github.carlos_emr.carlos.documentManager.annotation.BoundedPdfTask;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.commn.dao.CtlDocumentDao;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/** Real bounded workers and PDF extraction across independent authenticated HTTP sessions. */
@Tag("integration")
@Tag("document")
@Isolated("Saturates the shared process-wide PDF worker capacity")
class DocumentTextBoxesConcurrencyIntegrationTest {
    @Test
    void shouldRefuseAcrossSessionsAndRecoverWithoutCachingFailure_whenGlobalCapacityIsFull(@TempDir Path dir)
            throws Exception {
        try (PDDocument pdf = new PDDocument()) {
            pdf.addPage(new PDPage());
            pdf.save(dir.resolve("source.pdf").toFile());
        }
        EDoc doc = new EDoc();
        doc.setFileName("source.pdf");
        doc.setModule("demographic");
        doc.setModuleId("10");
        SecurityInfoManager security = mock(SecurityInfoManager.class);
        LoggedInInfo first = mock(LoggedInInfo.class);
        LoggedInInfo second = mock(LoggedInInfo.class);
        LoggedInInfo denied = mock(LoggedInInfo.class);
        when(security.hasPrivilege(any(), eq("_edoc"), eq(SecurityInfoManager.READ), isNull())).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(first, 10)).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(second, 10)).thenReturn(true);
        CarlosProperties properties = mock(CarlosProperties.class);
        when(properties.getDocumentDirectory()).thenReturn(dir.toString());
        int capacity = Math.max(8, 2 * Runtime.getRuntime().availableProcessors());
        CountDownLatch entered = new CountDownLatch(capacity);
        CountDownLatch release = new CountDownLatch(1);
        var callers = Executors.newFixedThreadPool(capacity * 3);
        List<Future<Integer>> held = new ArrayList<>();
        try (MockedStatic<EDocUtil> documents = mockStatic(EDocUtil.class);
             MockedStatic<CarlosProperties> configuration = mockStatic(CarlosProperties.class)) {
            documents.when(() -> EDocUtil.getDoc("42")).thenReturn(doc);
            configuration.when(CarlosProperties::getInstance).thenReturn(properties);
            for (int i = 0; i < capacity; i++) {
                held.add(callers.submit(() -> BoundedPdfTask.runWithin(30, "session-shared-pdf", () -> {
                    entered.countDown();
                    release.await();
                    return 1;
                })));
            }
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            for (int i = 0; i < capacity * 2; i++) {
                held.add(callers.submit(() -> BoundedPdfTask.runWithin(30, "waiting-other-session", () -> 1)));
            }
            awaitFullAdmissionQueue(capacity * 2);
            for (LoggedInInfo session : List.of(first, second)) {
                MockHttpServletResponse response = request(security, session);
                assertThat(response.getStatus()).isEqualTo(503);
                assertThat(response.getHeader("Retry-After")).isEqualTo("1");
                assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
                JsonNode payload = new ObjectMapper().readTree(response.getContentAsString());
                assertThat(payload.path("retryable").asBoolean()).isTrue();
                assertThat(payload.path("textLayerRead").asBoolean()).isFalse();
                assertThat(payload.path("words").size()).isZero();
            }
            assertThatThrownBy(() -> request(security, denied)).isInstanceOf(SecurityException.class);
            release.countDown();
            for (Future<Integer> future : held) {
                assertThat(future.get(10, TimeUnit.SECONDS)).isEqualTo(1);
            }
            for (LoggedInInfo session : List.of(first, second)) {
                MockHttpServletResponse response = request(security, session);
                assertThat(response.getStatus()).isEqualTo(200);
                JsonNode payload = new ObjectMapper().readTree(response.getContentAsString());
                assertThat(payload.path("textLayerRead").asBoolean()).isTrue();
                assertThat(payload.path("hasTextLayer").asBoolean()).isFalse();
                assertThat(payload.path("words").size()).isZero();
            }
            // Another session's successful read must not bypass the denied session's authorization.
            assertThatThrownBy(() -> request(security, denied)).isInstanceOf(SecurityException.class);
        } finally {
            release.countDown();
            callers.shutdown();
            assertThat(callers.awaitTermination(35, TimeUnit.SECONDS)).isTrue();
        }
    }

    @SuppressWarnings("java:S2925") // bounded observation of the actual process-wide admission queue
    private static void awaitFullAdmissionQueue(int expected) throws Exception {
        var queued = BoundedPdfTask.class.getDeclaredMethod("queuedTaskCount");
        queued.setAccessible(true);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while ((Integer) queued.invoke(null) != expected && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
        assertThat((Integer) queued.invoke(null)).isEqualTo(expected);
    }

    private MockHttpServletResponse request(SecurityInfoManager security, LoggedInInfo info) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/documentManager/DocumentTextBoxes");
        request.setParameter("docId", "42");
        request.setParameter("page", "1");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), info);
        MockHttpServletResponse response = new MockHttpServletResponse();
        try (MockedStatic<ServletActionContext> context = mockStatic(ServletActionContext.class)) {
            context.when(ServletActionContext::getRequest).thenReturn(request);
            context.when(ServletActionContext::getResponse).thenReturn(response);
            new DocumentTextBoxes2Action(security, mock(CtlDocumentDao.class)).execute();
        }
        return response;
    }
}
