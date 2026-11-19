/*
 * Copyright (c) 2026 CARLOS EMR Project. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.eform;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

/**
 * The links {@code oscarOPEN} buttons issue and the linked eForm's save accepts (issue #4764 follow-up).
 */
@Tag("unit")
@Tag("eform")
@DisplayName("eForm opener links")
class EFormOpenerLinksUnitTest {

    private static final String SESSION_KEY = EFormOpenerLinks.class.getName();

    private final MockHttpSession session = new MockHttpSession();

    private String issue(MockHttpSession target, String button) {
        return EFormOpenerLinks.issue(target, "doc1", "123", "13", button, "12");
    }

    @Test
    @DisplayName("should accept the link an opener issued when the eForm it opens is saved")
    void shouldAcceptIssuedLink_whenItsTargetEFormIsSaved() {
        String link = issue(session, "openLinked");

        assertThat(link).isEqualTo("doc1_123_13_openLinked");
        assertThat(EFormOpenerLinks.wasIssued(session, link, "doc1", "123", "12")).isTrue();
    }

    @Test
    @DisplayName("should refuse a well-formed link this session never issued")
    void shouldRefuseLink_whenNeverIssued() {
        assertThat(EFormOpenerLinks.wasIssued(session, "doc1_123_13_openLinked", "doc1", "123", "12")).isFalse();
    }

    @Test
    @DisplayName("should refuse an issued link when another eForm is saved with it")
    void shouldRefuseLink_whenAnotherEFormIsSaved() {
        String link = issue(session, "openLinked");

        assertThat(EFormOpenerLinks.wasIssued(session, link, "doc1", "123", "99")).isFalse();
    }

    @Test
    @DisplayName("should refuse an issued link for another patient or another provider")
    void shouldRefuseLink_whenPatientOrProviderDiffers() {
        String link = issue(session, "openLinked");

        assertThat(EFormOpenerLinks.wasIssued(session, link, "doc1", "124", "12")).isFalse();
        assertThat(EFormOpenerLinks.wasIssued(session, link, "doc1", "12", "12")).isFalse();
        assertThat(EFormOpenerLinks.wasIssued(session, link, "doc2", "123", "12")).isFalse();
    }

    @Test
    @DisplayName("should refuse a link another session issued")
    void shouldRefuseLink_whenIssuedInAnotherSession() {
        String link = issue(new MockHttpSession(), "openLinked");

        assertThat(EFormOpenerLinks.wasIssued(session, link, "doc1", "123", "12")).isFalse();
    }

    @Test
    @DisplayName("should keep accepting a link after a save, so a retried save is still linked")
    void shouldKeepLink_afterItIsAccepted() {
        String link = issue(session, "openLinked");

        assertThat(EFormOpenerLinks.wasIssued(session, link, "doc1", "123", "12")).isTrue();
        assertThat(EFormOpenerLinks.wasIssued(session, link, "doc1", "123", "12")).isTrue();
    }

    @Test
    @DisplayName("should issue nothing when the parts do not form a link or there is no session")
    void shouldIssueNothing_forInvalidParts() {
        assertThat(EFormOpenerLinks.issue(session, "doc1", "123", "13", "bad name", "12")).isNull();
        assertThat(EFormOpenerLinks.issue(session, "toolongprovider", "123", "13", "openLinked", "12")).isNull();
        assertThat(EFormOpenerLinks.issue(session, "doc1", "123", "13", "openLinked", "12; x")).isNull();
        assertThat(EFormOpenerLinks.issue(null, "doc1", "123", "13", "openLinked", "12")).isNull();
        assertThat(session.getAttribute(SESSION_KEY)).isNull();
        assertThat(EFormOpenerLinks.wasIssued(null, "doc1_123_13_openLinked", "doc1", "123", "12")).isFalse();
    }

    @Test
    @DisplayName("should drop the link issued longest ago once the session holds the most it keeps")
    void shouldDropOldestLink_whenBoundIsReached() {
        List<String> links = new ArrayList<>();
        for (int i = 0; i <= EFormOpenerLinks.MAX_LINKS; i++) links.add(issue(session, "button" + i));

        assertThat(EFormOpenerLinks.wasIssued(session, links.get(0), "doc1", "123", "12")).isFalse();
        assertThat(links.subList(1, links.size()))
                .allMatch(link -> EFormOpenerLinks.wasIssued(session, link, "doc1", "123", "12"));
    }

    @Test
    @DisplayName("should keep a re-rendered opener's link as the most recent")
    void shouldKeepReissuedLink_whenOthersAreIssuedAfterIt() {
        String kept = issue(session, "kept");
        for (int i = 0; i < EFormOpenerLinks.MAX_LINKS - 1; i++) issue(session, "button" + i);
        issue(session, "kept");
        issue(session, "oneMore");

        assertThat(EFormOpenerLinks.wasIssued(session, kept, "doc1", "123", "12")).isTrue();
        assertThat(EFormOpenerLinks.wasIssued(session, "doc1_123_13_button0", "doc1", "123", "12")).isFalse();
    }

    @Test
    @DisplayName("should lose no link and throw nothing when windows of one session issue and check at once")
    void shouldKeepEveryLink_whenIssuedAndCheckedConcurrently() throws Exception {
        int threads = 8;
        int perThread = EFormOpenerLinks.MAX_LINKS / threads;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        ConcurrentLinkedQueue<String> issued = new ConcurrentLinkedQueue<>();
        List<Future<?>> work = new ArrayList<>();
        try {
            for (int t = 0; t < threads; t++) {
                int thread = t;
                work.add(pool.submit(() -> {
                    start.await();
                    for (int i = 0; i < perThread; i++) {
                        String link = issue(session, "t" + thread + "b" + i);
                        issued.add(link);
                        for (String earlier : issued) EFormOpenerLinks.wasIssued(session, earlier, "doc1", "123", "12");
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : work) future.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        assertThat(issued).hasSize(threads * perThread)
                .allMatch(link -> EFormOpenerLinks.wasIssued(session, link, "doc1", "123", "12"));
    }

    @Test
    @DisplayName("should keep its links when the session is serialized and restored")
    void shouldKeepLinks_whenSessionIsSerialized() throws Exception {
        String link = issue(session, "openLinked");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(session.getAttribute(SESSION_KEY));
        }
        MockHttpSession restored = new MockHttpSession();
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            restored.setAttribute(SESSION_KEY, in.readObject());
        }

        assertThat(EFormOpenerLinks.wasIssued(restored, link, "doc1", "123", "12")).isTrue();
    }
}
