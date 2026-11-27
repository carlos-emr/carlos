/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.messenger.pageUtil;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.carlos_emr.carlos.commn.dao.SecObjPrivilegeDao;
import io.github.carlos_emr.carlos.test.web.JspRenderServer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.stream.Stream;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Renders the Messenger sent page, the {@code success} result of {@code messenger/CreateMessage},
 * with the request attributes {@link MsgCreateMessage2Action} sets after a send. 68ee754c53 (#6153)
 * added three warnings for steps that fail after the message has gone (patient link, saved
 * attachment cleanup, archiving the message replied to); each must appear alone when the action
 * reports it, in the page's language, and none when every step succeeded. The recipient list in
 * the confirmation stays HTML-encoded. Before #6336 the action test stopped at the attributes and
 * the packaged check only saw the page without a warning.
 */
@Tag("integration")
@Tag("messenger")
class SentMessageJspIntegrationTest {
    private static final String PAGE = "/WEB-INF/jsp/messenger/SentMessage.jsp";
    private static final String WARNINGS = "[data-carlos-patient-link-after-send-failed], "
            + "[data-carlos-attachments-after-send-failed], [data-carlos-archive-after-send-failed]";
    private static final List<String> AFTER_SEND_FAILURES = List.of(
            MsgCreateMessage2Action.PATIENT_LINK_AFTER_SEND_FAILED,
            MsgCreateMessage2Action.ATTACHMENTS_AFTER_SEND_FAILED,
            MsgCreateMessage2Action.ARCHIVE_AFTER_SEND_FAILED);

    @TempDir
    static Path directory;
    private static JspRenderServer server;

    @BeforeAll
    static void startServer() throws Exception {
        server = JspRenderServer.builder(directory)
                .jsp(PAGE)
                .bean(SecObjPrivilegeDao.class, JspRenderServer.privilegeDaoGranting("doctor", "x"))
                .start();
    }

    @AfterAll
    static void stopServer() throws Exception {
        if (server != null) {
            server.close();
        }
    }

    static Stream<Arguments> afterSendFailures() {
        return Stream.of("en", "fr-CA").flatMap(language -> Stream.of(
                Arguments.of(language, MsgCreateMessage2Action.PATIENT_LINK_AFTER_SEND_FAILED,
                        "data-carlos-patient-link-after-send-failed", "messenger.SentMessage.patientLinkFailed"),
                Arguments.of(language, MsgCreateMessage2Action.ATTACHMENTS_AFTER_SEND_FAILED,
                        "data-carlos-attachments-after-send-failed", "messenger.SentMessage.attachmentCleanupFailed"),
                Arguments.of(language, MsgCreateMessage2Action.ARCHIVE_AFTER_SEND_FAILED,
                        "data-carlos-archive-after-send-failed", "messenger.SentMessage.archiveFailed")));
    }

    @ParameterizedTest
    @MethodSource("afterSendFailures")
    void shouldShowOnlyTheReportedWarning_whenOneStepFailedAfterSending(String language, String failure,
            String marker, String key) throws Exception {
        ResourceBundle bundle = ResourceBundle.getBundle("oscarResources", Locale.forLanguageTag(language));

        Document html = server.request(PAGE)
                .header("Accept-Language", language)
                .render(request -> {
                    signIn(request);
                    request.setAttribute("SentMessageProvs", "FAKE Provider");
                    request.setAttribute(failure, Boolean.TRUE);
                })
                .assertOk()
                .document();

        Elements warnings = html.select(WARNINGS);
        assertThat(warnings).hasSize(1);
        Element warning = warnings.first();
        assertThat(warning.hasAttr(marker)).isTrue();
        assertThat(warning.attr("role")).isEqualTo("alert");
        assertThat(warning.hasClass("alert-warning")).isTrue();
        assertThat(warning.text()).isEqualTo(bundle.getString(key));
        assertThat(html.select("div.alert-success[role=alert]")).hasSize(1);
    }

    @Test
    void shouldShowNoAfterSendWarning_whenEveryStepSucceeded() throws Exception {
        Document html = server.render(PAGE, request -> {
            signIn(request);
            request.setAttribute("SentMessageProvs", "FAKE Provider");
        }).assertOk().document();

        assertThat(html.select(WARNINGS)).isEmpty();
        assertThat(html.select("div.alert-warning")).isEmpty();
        assertThat(html.select("div.alert-success[role=alert]")).hasSize(1);

        Document cleared = server.render(PAGE, request -> {
            signIn(request);
            request.setAttribute("SentMessageProvs", "FAKE Provider");
            AFTER_SEND_FAILURES.forEach(failure -> request.setAttribute(failure, Boolean.FALSE));
        }).assertOk().document();

        assertThat(cleared.select(WARNINGS)).isEmpty();
    }

    @Test
    void shouldEncodeTheRecipients_inTheSentConfirmation() throws Exception {
        String recipients = "FAKE Provider <img src=x onerror=alert(1)>, FAKE Second & Co";
        ResourceBundle bundle = ResourceBundle.getBundle("oscarResources", Locale.ENGLISH);

        JspRenderServer.RenderedPage page = server.render(PAGE, request -> {
            signIn(request);
            request.setAttribute("SentMessageProvs", recipients);
        }).assertOk();

        Element confirmation = page.document().selectFirst("div.alert-success[role=alert]");
        assertThat(confirmation.text())
                .isEqualTo(bundle.getString("messenger.SentMessage.msgMessageSentTo") + " " + recipients);
        assertThat(confirmation.select("img")).isEmpty();
        assertThat(page.body())
                .contains("&lt;img src=x onerror=alert(1)&gt;, FAKE Second &amp; Co")
                .doesNotContain("<img src=x");
    }

    /** The signed-in session the page's {@code _msg} check and Messenger session test read. */
    private static void signIn(HttpServletRequest request) {
        HttpSession session = request.getSession();
        session.setAttribute("userrole", "doctor");
        session.setAttribute("user", "999998");
        MsgSessionBean messenger = new MsgSessionBean();
        messenger.setProviderNo("999998");
        session.setAttribute("msgSessionBean", messenger);
    }
}
