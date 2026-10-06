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
package io.github.carlos_emr.carlos.eform.actions;

import io.github.carlos_emr.carlos.email.core.EmailAttachmentSettings;
import io.github.carlos_emr.carlos.email.core.EmailComposeStaging;
import io.github.carlos_emr.carlos.documentManager.DocumentAttachmentManager;
import io.github.carlos_emr.carlos.managers.EformDataManager;
import io.github.carlos_emr.carlos.managers.EmailManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
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

    /** Stages a draft the way the eForm save does and returns its one-time key. */
    private static String stage(AddEForm2Action writer, MockHttpSession session, String fid,
            EmailAttachmentSettings settings) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        Method method = AddEForm2Action.class.getDeclaredMethod("addEmailAttachmentsToSession",
                HttpServletRequest.class, String.class, EmailAttachmentSettings.class);
        method.setAccessible(true);
        return (String) method.invoke(writer, request, fid, settings);
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    /** Waits until {@code thread} is blocked on the session's own mutex, not on some other lock. */
    private static void awaitBlocked(Thread thread, MockHttpSession session) throws InterruptedException {
        Object mutex = org.springframework.web.util.WebUtils.getSessionMutex(session);
        String lockName = mutex.getClass().getName() + '@' + Integer.toHexString(System.identityHashCode(mutex));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (thread.getState() != Thread.State.BLOCKED && thread.isAlive() && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
        assertThat(thread.getState()).isEqualTo(Thread.State.BLOCKED);
        assertThat(java.lang.management.ManagementFactory.getThreadMXBean().getThreadInfo(thread.getId()).getLockName())
                .isEqualTo(lockName);
    }

    @Test
    @DisplayName("should keep both whole drafts when two patients' saves overlap")
    void shouldKeepBothWholeDrafts_whenTwoSavesOverlap() throws Exception {
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
        FutureTask<String> first = new FutureTask<>(() -> stage(writer, session, "40001", a));
        FutureTask<String> second = new FutureTask<>(() -> stage(writer, session, "40002", b));
        Thread threadA = new Thread(first, "FAKE-save-A");
        Thread threadB = new Thread(second, "FAKE-save-B");
        threadA.start();
        try {
            await(publishedA);
            threadB.start();
            // The old field-by-field writer allowed B to finish here, then A resumed its remaining
            // fields, mixing B's fdid with A's patient/body/documents. B must now wait for publication.
            awaitBlocked(threadB, session);
        } finally {
            resumeA.countDown();
            threadA.join(5000);
            threadB.join(5000);
        }
        String keyA = first.get(5, TimeUnit.SECONDS);
        String keyB = second.get(5, TimeUnit.SECONDS);
        // Neither save replaced the other (#4101): each window's key still names its own whole draft.
        EmailComposeStaging.Draft draftB = EmailComposeStaging.take(session, keyB);
        assertThat(draftB.fid()).isEqualTo("40002");
        assertThat(draftB.settings()).isSameAs(b);
        assertThat(draftB.settings().fdid()).isEqualTo("20002");
        assertThat(draftB.settings().demographicNo()).isEqualTo("10002");
        assertThat(draftB.settings().subjectEmail()).isEqualTo("FAKE subject B");
        assertThat(draftB.settings().bodyEmail()).isEqualTo("FAKE message B");
        assertThat(draftB.settings().attachedDocuments()).containsExactly("30002");
        EmailComposeStaging.Draft draftA = EmailComposeStaging.take(session, keyA);
        assertThat(draftA.fid()).isEqualTo("40001");
        assertThat(draftA.settings()).isSameAs(a);
        assertThat(EmailComposeStaging.take(session, keyA)).isNull();
        assertThat(EmailComposeStaging.take(session, keyB)).isNull();
    }

    @Test
    @DisplayName("should keep the next patient's save while another draft is being taken")
    void shouldKeepNextSave_whileAnotherDraftIsTaken() throws Exception {
        AddEForm2Action writer = writer();
        CountDownLatch readA = new CountDownLatch(1);
        CountDownLatch resumeTake = new CountDownLatch(1);
        MockHttpSession session = new MockHttpSession() {
            @Override public Object getAttribute(String name) {
                Object value = super.getAttribute(name);
                if (name.equals(EmailComposeStaging.class.getName() + ".drafts")
                        && Thread.currentThread().getName().equals("FAKE-take-A")) {
                    readA.countDown();
                    await(resumeTake);
                }
                return value;
            }
        };
        EmailAttachmentSettings a = settings("10001", "20001", "30001", "A");
        EmailAttachmentSettings b = settings("10002", "20002", "30002", "B");
        String keyA = stage(writer, session, "40001", a);
        FutureTask<EmailComposeStaging.Draft> take = new FutureTask<>(() -> EmailComposeStaging.take(session, keyA));
        FutureTask<String> save = new FutureTask<>(() -> stage(writer, session, "40002", b));
        Thread reader = new Thread(take, "FAKE-take-A");
        Thread nextWriter = new Thread(save, "FAKE-save-B");
        reader.start();
        try {
            await(readA);
            nextWriter.start();
            awaitBlocked(nextWriter, session);
        } finally {
            resumeTake.countDown();
            reader.join(5000);
            nextWriter.join(5000);
        }
        String keyB = save.get(5, TimeUnit.SECONDS);
        assertThat(take.get(5, TimeUnit.SECONDS).settings()).isSameAs(a);
        assertThat(EmailComposeStaging.take(session, keyB).settings()).isSameAs(b);
        assertThat(EmailComposeStaging.take(session, keyA)).isNull();
        assertThat(EmailComposeStaging.take(session, keyB)).isNull();
    }

    @Test
    @DisplayName("should send the draft's one-time key with the redirect to the compose action")
    void shouldRedirectWithDraftKey_toComposeAction() throws Exception {
        AddEForm2Action writer = writer();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContextPath("/carlos");
        org.springframework.mock.web.MockHttpServletResponse response = new org.springframework.mock.web.MockHttpServletResponse();
        org.springframework.test.util.ReflectionTestUtils.setField(writer, "request", request);
        org.springframework.test.util.ReflectionTestUtils.setField(writer, "response", response);
        String key = stage(writer, new MockHttpSession(), "40001", settings("10001", "20001", "30001", "A"));

        Method redirect = AddEForm2Action.class.getDeclaredMethod("redirectToEmailCompose", String.class, String.class);
        redirect.setAccessible(true);
        redirect.invoke(writer, "40001", key);

        assertThat(response.getRedirectedUrl()).isEqualTo(
                "/carlos/email/emailComposeAction?method=prepareComposeEFormMailer&fid=40001&draft=" + key);
        assertThat(EmailComposeStaging.isKey(key)).isTrue();
    }
}
