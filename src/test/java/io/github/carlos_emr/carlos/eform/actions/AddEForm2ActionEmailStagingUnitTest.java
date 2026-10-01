/** Copyright (c) 2026 CARLOS Contributors. Published under the GPL version 2 or later. */
package io.github.carlos_emr.carlos.eform.actions;

import io.github.carlos_emr.carlos.email.core.EmailAttachmentSettings;
import io.github.carlos_emr.carlos.email.core.EmailComposeStaging;
import io.github.carlos_emr.carlos.documentManager.DocumentAttachmentManager;
import io.github.carlos_emr.carlos.managers.EformDataManager;
import io.github.carlos_emr.carlos.managers.EmailManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@Tag("unit")
@Tag("security")
class AddEForm2ActionEmailStagingUnitTest extends CarlosUnitTestBase {
    private static EmailAttachmentSettings settings(String patient, String fdid, String document, String name) {
        return new EmailAttachmentSettings(fdid, patient, null, new String[]{document}, null, null, null,
                true, false, false, false, false, false, "fake@example.com", "FAKE subject " + name,
                "FAKE message " + name, null, "FULL");
    }

    private AddEForm2Action writer() {
        registerMock(SecurityInfoManager.class, mock(SecurityInfoManager.class));
        registerMock(EformDataManager.class, mock(EformDataManager.class));
        registerMock(DocumentAttachmentManager.class, mock(DocumentAttachmentManager.class));
        registerMock(EmailManager.class, mock(EmailManager.class));
        try (var servlet = org.mockito.Mockito.mockStatic(org.apache.struts2.ServletActionContext.class)) {
            return new AddEForm2Action();
        }
    }

    private static void stage(AddEForm2Action writer, MockHttpSession session, String fid,
            EmailAttachmentSettings settings) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        Method method = AddEForm2Action.class.getDeclaredMethod("addEmailAttachmentsToSession",
                HttpServletRequest.class, String.class, EmailAttachmentSettings.class);
        method.setAccessible(true);
        method.invoke(writer, request, fid, settings);
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    private static void awaitBlocked(Thread thread) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (thread.getState() != Thread.State.BLOCKED && thread.isAlive() && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
        assertThat(thread.getState()).isEqualTo(Thread.State.BLOCKED);
    }

    @Test
    void overlappingPatientSavesPublishWholeDrafts() throws Exception {
        AddEForm2Action writer = writer();
        CountDownLatch publishedA = new CountDownLatch(1);
        CountDownLatch resumeA = new CountDownLatch(1);
        MockHttpSession session = new MockHttpSession() {
            @Override public void setAttribute(String name, Object value) {
                super.setAttribute(name, value);
                if (Thread.currentThread().getName().equals("FAKE-save-A")) {
                    publishedA.countDown();
                    await(resumeA);
                }
            }
        };
        EmailAttachmentSettings a = settings("10001", "20001", "30001", "A");
        EmailAttachmentSettings b = settings("10002", "20002", "30002", "B");
        FutureTask<Void> first = new FutureTask<>(() -> { stage(writer, session, "40001", a); return null; });
        FutureTask<Void> second = new FutureTask<>(() -> { stage(writer, session, "40002", b); return null; });
        Thread threadA = new Thread(first, "FAKE-save-A");
        Thread threadB = new Thread(second, "FAKE-save-B");
        threadA.start();
        try {
            await(publishedA);
            threadB.start();
            // The old field-by-field writer allowed B to finish here, then A resumed its remaining
            // fields, mixing B's fdid with A's patient/body/documents. B must now wait for publication.
            awaitBlocked(threadB);
        } finally {
            resumeA.countDown();
            threadA.join(5000);
            threadB.join(5000);
        }
        first.get(5, TimeUnit.SECONDS);
        second.get(5, TimeUnit.SECONDS);
        EmailComposeStaging.Draft draft = EmailComposeStaging.take(session);
        assertThat(draft.fid()).isEqualTo("40002");
        assertThat(draft.settings()).isSameAs(b);
        assertThat(draft.settings().fdid()).isEqualTo("20002");
        assertThat(draft.settings().demographicNo()).isEqualTo("10002");
        assertThat(draft.settings().subjectEmail()).isEqualTo("FAKE subject B");
        assertThat(draft.settings().bodyEmail()).isEqualTo("FAKE message B");
        assertThat(draft.settings().attachedDocuments()).containsExactly("30002");
        assertThat(EmailComposeStaging.take(session)).isNull();
    }

    @Test
    void takingOneDraftCannotEraseTheNextPatientSave() throws Exception {
        AddEForm2Action writer = writer();
        CountDownLatch readA = new CountDownLatch(1);
        CountDownLatch resumeTake = new CountDownLatch(1);
        MockHttpSession session = new MockHttpSession() {
            @Override public Object getAttribute(String name) {
                Object value = super.getAttribute(name);
                if (value instanceof EmailComposeStaging.Draft && Thread.currentThread().getName().equals("FAKE-take-A")) {
                    readA.countDown();
                    await(resumeTake);
                }
                return value;
            }
        };
        EmailAttachmentSettings a = settings("10001", "20001", "30001", "A");
        EmailAttachmentSettings b = settings("10002", "20002", "30002", "B");
        stage(writer, session, "40001", a);
        FutureTask<EmailComposeStaging.Draft> take = new FutureTask<>(() -> EmailComposeStaging.take(session));
        FutureTask<Void> save = new FutureTask<>(() -> { stage(writer, session, "40002", b); return null; });
        Thread reader = new Thread(take, "FAKE-take-A");
        Thread nextWriter = new Thread(save, "FAKE-save-B");
        reader.start();
        try {
            await(readA);
            nextWriter.start();
            awaitBlocked(nextWriter);
        } finally {
            resumeTake.countDown();
            reader.join(5000);
            nextWriter.join(5000);
        }
        save.get(5, TimeUnit.SECONDS);
        assertThat(take.get(5, TimeUnit.SECONDS).settings()).isSameAs(a);
        assertThat(EmailComposeStaging.take(session).settings()).isSameAs(b);
        assertThat(EmailComposeStaging.take(session)).isNull();
    }
}
